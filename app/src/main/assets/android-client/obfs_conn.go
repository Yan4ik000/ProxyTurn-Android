package main

import (
	"crypto/cipher"
	"log"
	"net"
	"sync"
	"time"
)

type obfsPacketConn struct {
	inner      net.PacketConn
	aead       cipher.AEAD
	cfg        *ObfsConfig
	state      *ObfsState
	sessionID  int
	readMu     sync.Mutex
	writeMu    sync.Mutex
	readBuf    []byte
	writeBuf   []byte
	readNonce  [12]byte
	writeNonce [12]byte
}

func newObfsPacketConn(inner net.PacketConn, key []byte, cfg *ObfsConfig, state *ObfsState, sessionID int) (*obfsPacketConn, error) {
	aead, err := newObfsAEAD(key)
	if err != nil {
		return nil, err
	}
	wrappedSize := obfsWrapWireLen(readBufSize, cfg)
	return &obfsPacketConn{
		inner:     inner,
		aead:      aead,
		cfg:       cfg,
		state:     state,
		sessionID: sessionID,
		readBuf:   make([]byte, wrappedSize),
		writeBuf:  make([]byte, wrappedSize),
	}, nil
}

func (c *obfsPacketConn) ReadFrom(p []byte) (int, net.Addr, error) {
	c.readMu.Lock()
	defer c.readMu.Unlock()
	for {
		n, addr, err := c.inner.ReadFrom(c.readBuf)
		if err != nil {
			return 0, nil, err
		}
		wire := c.readBuf[:n]
		if !obfsIsRTPPacket(wire) {
			log.Printf("[СЕССИЯ #%d] OBFS unwrap: unexpected packet (n=%d)", c.sessionID, n)
			continue
		}
		plainLen, err := obfsUnwrapPacket(c.aead, wire, p, &c.readNonce)
		if err != nil {
			log.Printf("[СЕССИЯ #%d] OBFS unwrap: %v (n=%d)", c.sessionID, err, n)
			continue
		}
		return plainLen, addr, nil
	}
}

func (c *obfsPacketConn) WriteTo(p []byte, addr net.Addr) (int, error) {
	c.writeMu.Lock()
	defer c.writeMu.Unlock()
	wrappedLen, err := obfsWrapPacketInto(c.writeBuf, c.aead, p, c.cfg, c.state, &c.writeNonce)
	if err != nil {
		return 0, err
	}
	if _, err = c.inner.WriteTo(c.writeBuf[:wrappedLen], addr); err != nil {
		return 0, err
	}
	return len(p), nil
}

func (c *obfsPacketConn) Close() error {
	return c.inner.Close()
}

func (c *obfsPacketConn) LocalAddr() net.Addr {
	return c.inner.LocalAddr()
}

func (c *obfsPacketConn) SetDeadline(t time.Time) error {
	return c.inner.SetDeadline(t)
}

func (c *obfsPacketConn) SetReadDeadline(t time.Time) error {
	return c.inner.SetReadDeadline(t)
}

func (c *obfsPacketConn) SetWriteDeadline(t time.Time) error {
	return c.inner.SetWriteDeadline(t)
}

var _ net.PacketConn = (*obfsPacketConn)(nil)
