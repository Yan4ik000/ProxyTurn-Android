package main

import (
	"context"
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
	scaleUpCooldown        = 5 * time.Second
	scaleDownCooldown      = 45 * time.Second
	scaleSettleDelay       = 3 * time.Second
	scaleUpRatePerWorker   = 150 * 1024
	scaleDownRatePerWorker = 100 * 1024
	awakeMinGroups         = 4
	sleepMinGroups         = 2
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
	a.mu.Unlock()
	cancel()
}

func (a *Autoscaler) isSpawning() bool {
	a.mu.Lock()
	defer a.mu.Unlock()
	return a.spawning
}

func (a *Autoscaler) groups() int {
	a.mu.Lock()
	defer a.mu.Unlock()
	return len(a.groupCancels)
}

func (a *Autoscaler) Run() {
	a.configPending.Store(1)
	a.floorGroups = awakeMinGroups
	a.spawnGroup()

	ticker := time.NewTicker(scaleSampleInterval)
	defer ticker.Stop()

	upStreak := 0
	downStreak := 0
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

		// Пока идёт спавн группы или группа ещё гаснет после scale-действия,
		// таймеры sustain не тикают: отсчёт начинается после завершения процесса.
		if a.isSpawning() || time.Since(lastScale) < scaleSettleDelay {
			upStreak = 0
			downStreak = 0
			continue
		}

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

		groups := a.groups()

		if a.floorGroups == sleepMinGroups && upStreak >= scaleUpSustainSecs {
			a.floorGroups = awakeMinGroups
			lastWake = time.Now()
			upStreak = 0
			downStreak = 0
			continue
		}

		if a.floorGroups == awakeMinGroups && groups <= awakeMinGroups &&
			downStreak >= sleepAfterSecs &&
			time.Since(lastWake) >= minAwakeSecs*time.Second {
			a.floorGroups = sleepMinGroups
			for len(a.groupCancels) > sleepMinGroups {
				a.dropLastGroup()
			}
			lastScale = time.Now()
			upStreak = 0
			downStreak = 0
			continue
		}

		if groups < a.floorGroups && groups < a.maxGroups {
			a.spawnGroup()
			lastScale = time.Now()
			upStreak = 0
			downStreak = 0
			continue
		}

		if upStreak >= scaleUpSustainSecs && groups < a.maxGroups &&
			time.Since(lastScale) >= scaleUpCooldown {
			a.spawnGroup()
			lastScale = time.Now()
			upStreak = 0
			downStreak = 0
			continue
		}

		if downStreak >= scaleDownSustainSecs && groups > a.floorGroups &&
			time.Since(lastScale) >= scaleDownCooldown {
			a.dropLastGroup()
			lastScale = time.Now()
			upStreak = 0
			downStreak = 0
		}
	}
}
