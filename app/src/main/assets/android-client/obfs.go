package main

import (
	"crypto/cipher"
	"crypto/rand"
	"encoding/binary"
	"errors"
	"fmt"

	"golang.org/x/crypto/chacha20poly1305"
)

func newObfsAEAD(key []byte) (cipher.AEAD, error) {
	if len(key) != wrapKeyLen {
		return nil, fmt.Errorf("obfs: key must be %d bytes", wrapKeyLen)
	}
	return chacha20poly1305.New(key)
}

type ObfsConfig struct {
	SSRC        uint32
	PayloadType uint8
	PaddingMax  int
}

func NewObfsConfig(mode string) *ObfsConfig {
	var buf [4]byte
	rand.Read(buf[:])

	pt := uint8(111)
	pad := 24
	if mode == "video" {
		pt = 96
		pad = 60
	}

	return &ObfsConfig{
		SSRC:        binary.BigEndian.Uint32(buf[:]),
		PayloadType: pt,
		PaddingMax:  pad,
	}
}

type ObfsState struct {
	initSeq uint16
	initTs  uint32
	count   uint64
	padSeed uint64
}

func NewObfsState() *ObfsState {
	var buf [14]byte
	rand.Read(buf[:])
	return &ObfsState{
		initSeq: binary.BigEndian.Uint16(buf[0:2]),
		initTs:  binary.BigEndian.Uint32(buf[2:6]),
		count:   0,
		padSeed: binary.BigEndian.Uint64(buf[6:14]),
	}
}

func obfsBuildNonce(dst *[12]byte, ssrc uint32, seq uint16, ts uint32) {
	binary.BigEndian.PutUint32(dst[0:4], ssrc)
	binary.BigEndian.PutUint16(dst[4:6], seq)
	binary.BigEndian.PutUint32(dst[8:12], ts)
}

func obfsWrapWireLen(payloadLen int, cfg *ObfsConfig) int {
	pad := cfg.PaddingMax
	if pad < 1 {
		pad = 1
	}
	return 12 + payloadLen + chacha20poly1305.Overhead + pad
}

func obfsWrapPacketInto(dst []byte, aead cipher.AEAD, payload []byte, cfg *ObfsConfig, state *ObfsState, nonce *[12]byte) (int, error) {
	if len(payload) == 0 {
		return 0, errors.New("obfs: empty payload")
	}

	c := state.count
	state.count++

	seq := state.initSeq + uint16(c)
	ts := state.initTs + uint32(c)*960 + uint32(c>>16)

	padRand := 0
	x := state.padSeed + c*0x9e3779b97f4a7c15
	if cfg.PaddingMax > 0 {
		x ^= x >> 30
		x *= 0xbf58476d1ce4e5b9
		x ^= x >> 27
		x *= 0x94d049bb133111eb
		x ^= x >> 31
		padRand = int(x % uint64(cfg.PaddingMax))
	}
	padTotal := padRand + 1

	outLen := 12 + len(payload) + chacha20poly1305.Overhead + padTotal
	if outLen > len(dst) {
		return 0, fmt.Errorf("obfs: dst buffer too small (%d > %d)", outLen, len(dst))
	}

	dst[0] = 0x80 | 0x20
	dst[1] = cfg.PayloadType & 0x7F
	binary.BigEndian.PutUint16(dst[2:4], seq)
	binary.BigEndian.PutUint32(dst[4:8], ts)
	binary.BigEndian.PutUint32(dst[8:12], cfg.SSRC)

	obfsBuildNonce(nonce, cfg.SSRC, seq, ts)
	sealed := aead.Seal(dst[12:12], nonce[:], payload, dst[:12])

	padStart := 12 + len(sealed)
	if padRand > 0 {
		for i := 0; i < padRand; i++ {
			dst[padStart+i] = byte(x >> ((i % 8) * 8))
		}
	}

	dst[outLen-1] = byte(padTotal)

	return outLen, nil
}

func obfsUnwrapPacket(aead cipher.AEAD, wire, dst []byte, nonce *[12]byte) (int, error) {
	if len(wire) < 13 {
		return 0, errors.New("obfs: packet too short")
	}

	if (wire[0] >> 6) != 2 {
		return 0, errors.New("obfs: not RTP v2")
	}

	seq := binary.BigEndian.Uint16(wire[2:4])
	ts := binary.BigEndian.Uint32(wire[4:8])
	ssrc := binary.BigEndian.Uint32(wire[8:12])

	payloadEnd := len(wire)
	if wire[0]&0x20 != 0 {
		padLen := int(wire[len(wire)-1])
		if padLen == 0 || padLen > payloadEnd-12 {
			return 0, fmt.Errorf("obfs: invalid padding length %d", padLen)
		}
		payloadEnd -= padLen
	}

	ciphertextLen := payloadEnd - 12
	if ciphertextLen <= chacha20poly1305.Overhead {
		return 0, errors.New("obfs: no payload after stripping header/padding")
	}
	if ciphertextLen-chacha20poly1305.Overhead > len(dst) {
		return 0, errors.New("obfs: dst buffer too small")
	}

	obfsBuildNonce(nonce, ssrc, seq, ts)
	plain, err := aead.Open(dst[:0], nonce[:], wire[12:payloadEnd], wire[:12])
	if err != nil {
		return 0, fmt.Errorf("obfs: auth: %w", err)
	}

	return len(plain), nil
}

func obfsIsRTPPacket(wire []byte) bool {
	if len(wire) < 13 {
		return false
	}

	if (wire[0] >> 6) != 2 {
		return false
	}

	pt := wire[1] & 0x7F
	return pt == 111 || pt == 96
}
