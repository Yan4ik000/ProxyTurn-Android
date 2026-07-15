package main

import (
	"context"
	"sort"
	"sync"
	"sync/atomic"
	"time"
)

const (
	heartbeatBatchSize  = 9
	heartbeatBatchDelay = 75 * time.Millisecond
)

type heartbeatSession struct {
	id     uint64
	signal chan<- struct{}
}

type HeartbeatCoordinator struct {
	ctx      context.Context
	mu       sync.RWMutex
	nextID   atomic.Uint64
	sessions map[uint64]chan<- struct{}
}

func NewHeartbeatCoordinator(ctx context.Context) *HeartbeatCoordinator {
	c := &HeartbeatCoordinator{
		ctx:      ctx,
		sessions: make(map[uint64]chan<- struct{}),
	}
	go c.run()
	return c
}

func (c *HeartbeatCoordinator) Register(signal chan<- struct{}) uint64 {
	id := c.nextID.Add(1)
	c.mu.Lock()
	c.sessions[id] = signal
	c.mu.Unlock()
	return id
}

func (c *HeartbeatCoordinator) Unregister(id uint64) {
	c.mu.Lock()
	delete(c.sessions, id)
	c.mu.Unlock()
}

func (c *HeartbeatCoordinator) run() {
	ticker := time.NewTicker(keepaliveInterval)
	defer ticker.Stop()
	for {
		select {
		case <-c.ctx.Done():
			return
		case <-ticker.C:
			if !c.dispatch() {
				return
			}
		}
	}
}

func (c *HeartbeatCoordinator) dispatch() bool {
	c.mu.RLock()
	sessions := make([]heartbeatSession, 0, len(c.sessions))
	for id, signal := range c.sessions {
		sessions = append(sessions, heartbeatSession{id: id, signal: signal})
	}
	c.mu.RUnlock()

	sort.Slice(sessions, func(i, j int) bool {
		return sessions[i].id < sessions[j].id
	})

	for start := 0; start < len(sessions); start += heartbeatBatchSize {
		end := min(start+heartbeatBatchSize, len(sessions))
		for _, session := range sessions[start:end] {
			select {
			case session.signal <- struct{}{}:
			default:
			}
		}
		if end == len(sessions) {
			break
		}
		timer := time.NewTimer(heartbeatBatchDelay)
		select {
		case <-c.ctx.Done():
			if !timer.Stop() {
				<-timer.C
			}
			return false
		case <-timer.C:
		}
	}
	return true
}
