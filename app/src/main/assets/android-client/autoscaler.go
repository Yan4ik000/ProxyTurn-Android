package main

import (
	"context"
	"log"
	"net"
	"sync"
	"sync/atomic"
	"time"
)

const (
	scaleSampleInterval    = time.Second
	scaleUpThreshold       = 0.75
	scaleUpSustainSecs     = 2
	scaleDownThreshold     = 0.15
	scaleDownSustainSecs   = 20
	scaleUpCooldown        = 10 * time.Second
	scaleDownCooldown      = 45 * time.Second
	scaleUpRatePerWorker   = 150 * 1024
	scaleDownRatePerWorker = 100 * 1024
	awakeMinGroups         = 3
	sleepMinGroups         = 1
	sleepAfterSecs         = 300
	minAwakeSecs           = 90
)

type Autoscaler struct {
	ctx        context.Context
	tp         *TurnParams
	peer       *net.UDPAddr
	disp       *Dispatcher
	heartbeats *HeartbeatCoordinator
	localPort  string
	deviceID   string
	password   string
	stats      *Stats
	pauseFlag  *int32
	configCh   chan<- string
	maxGroups  int

	groupSeq      int
	workerSeq     int
	configPending atomic.Int32
	floorGroups   int

	mu           sync.Mutex
	groupCancels []context.CancelFunc
	spawning     bool
	wg           sync.WaitGroup
}

func (a *Autoscaler) spawnGroup() {
	a.groupSeq++
	groupID := a.groupSeq
	hashIndex := a.groupSeq - 1

	ids := make([]int, workersPerGroup)
	for i := range ids {
		a.workerSeq++
		ids[i] = a.workerSeq
	}

	getConfig := a.configPending.CompareAndSwap(1, 0)
	var cc chan<- string
	if getConfig {
		cc = a.configCh
	}

	groupCtx, cancel := context.WithCancel(a.ctx)
	spawned := make(chan struct{})
	done := make(chan struct{})

	a.mu.Lock()
	a.groupCancels = append(a.groupCancels, cancel)
	a.spawning = true
	a.mu.Unlock()

	a.wg.Add(1)
	go func() {
		defer a.wg.Done()
		defer close(done)
		WorkerGroup(groupCtx, groupID, hashIndex, a.tp, a.peer, a.disp, a.heartbeats, a.localPort,
			getConfig, cc, ids, a.pauseFlag, a.deviceID, a.password, a.stats,
			nil, nil, nil, spawned)
	}()

	go func() {
		select {
		case <-spawned:
		case <-done:
		case <-a.ctx.Done():
		}
		a.mu.Lock()
		a.spawning = false
		a.mu.Unlock()
	}()
}

func (a *Autoscaler) dropLastGroup() {
	a.mu.Lock()
	if len(a.groupCancels) == 0 {
		a.mu.Unlock()
		return
	}
	cancel := a.groupCancels[len(a.groupCancels)-1]
	a.groupCancels = a.groupCancels[:len(a.groupCancels)-1]
	remaining := len(a.groupCancels)
	a.mu.Unlock()
	log.Printf("[АВТО] Убираю группу воркеров, осталось групп: %d", remaining)
	cancel()
}

func (a *Autoscaler) Run() {
	a.configPending.Store(1)
	a.floorGroups = awakeMinGroups
	a.spawnGroup()

	ticker := time.NewTicker(scaleSampleInterval)
	defer ticker.Stop()

	upStreak := 0
	downStreak := 0
	statusTick := 0
	lastScale := time.Now()
	lastWake := time.Now()
	lastBytes := a.stats.TotalBytesUp.Load() + a.stats.TotalBytesDown.Load()

	for {
		select {
		case <-a.ctx.Done():
			a.wg.Wait()
			return
		case <-ticker.C:
		}

		if atomic.LoadInt32(a.pauseFlag) != 0 {
			upStreak = 0
			downStreak = 0
			continue
		}

		totalBytes := a.stats.TotalBytesUp.Load() + a.stats.TotalBytesDown.Load()
		rate := float64(totalBytes - lastBytes)
		lastBytes = totalBytes

		active := a.stats.ActiveConnections.Load()
		if active < 1 {
			active = 1
		}
		perWorkerRate := rate / float64(active)

		fill := a.disp.queueFillRatio()

		if fill >= scaleUpThreshold || perWorkerRate >= scaleUpRatePerWorker {
			upStreak++
		} else {
			upStreak = 0
		}
		if fill <= scaleDownThreshold && perWorkerRate <= scaleDownRatePerWorker {
			downStreak++
		} else {
			downStreak = 0
		}

		a.mu.Lock()
		groups := len(a.groupCancels)
		spawning := a.spawning
		a.mu.Unlock()

		statusTick++
		if statusTick >= 15 {
			statusTick = 0
			log.Printf("[АВТО] Скорость: %.0f КБ/с общая, %.0f КБ/с на воркера (активных %d), групп %d/%d",
				rate/1024, perWorkerRate/1024, active, groups, a.maxGroups)
		}

		if a.floorGroups == sleepMinGroups && upStreak >= scaleUpSustainSecs && !spawning {
			log.Printf("[АВТО] Нагрузка %.1f КБ/с на воркера — пробуждение до %d воркеров",
				perWorkerRate/1024, awakeMinGroups*workersPerGroup)
			a.floorGroups = awakeMinGroups
			lastWake = time.Now()
			upStreak = 0
			downStreak = 0
			continue
		}

		if a.floorGroups == awakeMinGroups && groups <= awakeMinGroups &&
			downStreak >= sleepAfterSecs && !spawning &&
			time.Since(lastWake) >= minAwakeSecs*time.Second {
			log.Printf("[АВТО] Нет нагрузки %d мин — снижаю базу до %d воркеров",
				sleepAfterSecs/60, sleepMinGroups*workersPerGroup)
			a.floorGroups = sleepMinGroups
			for len(a.groupCancels) > sleepMinGroups {
				a.dropLastGroup()
			}
			lastScale = time.Now()
			upStreak = 0
			downStreak = 0
			continue
		}

		if groups < a.floorGroups && groups < a.maxGroups && !spawning {
			a.spawnGroup()
			lastScale = time.Now()
			upStreak = 0
			downStreak = 0
			continue
		}

		if upStreak >= scaleUpSustainSecs && groups < a.maxGroups && !spawning &&
			time.Since(lastScale) >= scaleUpCooldown {
			log.Printf("[АВТО] Нагрузка %.1f МБ/с на воркера (очереди %.0f%%) уже %dс — добавляю группу (%d/%d)",
				perWorkerRate/1048576, fill*100, scaleUpSustainSecs, groups+1, a.maxGroups)
			a.spawnGroup()
			lastScale = time.Now()
			upStreak = 0
			downStreak = 0
			continue
		}

		if downStreak >= scaleDownSustainSecs && groups > a.floorGroups && !spawning &&
			time.Since(lastScale) >= scaleDownCooldown {
			log.Printf("[АВТО] Нагрузка %.1f КБ/с на воркера уже %dс — снижаю мощность",
				perWorkerRate/1024, scaleDownSustainSecs)
			a.dropLastGroup()
			lastScale = time.Now()
			upStreak = 0
			downStreak = 0
		}
	}
}
