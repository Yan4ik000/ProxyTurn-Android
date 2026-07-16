package main

import (
	"bytes"
	"net"
	"testing"
	"time"

	"github.com/cbeuw/connutil"
)

func TestObfsPacketConnRoundTrip(t *testing.T) {
	key := bytes.Repeat([]byte{0x5A}, wrapKeyLen)
	tests := []struct {
		name        string
		payloadType uint8
		paddingMax  int
		payloadSize int
	}{
		{name: "audio", payloadType: 111, paddingMax: 24, payloadSize: 1},
		{name: "video", payloadType: 96, paddingMax: 60, payloadSize: readBufSize},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			left, right := connutil.AsyncPacketPipe()
			defer left.Close()
			defer right.Close()

			cfg := &ObfsConfig{
				SSRC:        0x10203040,
				PayloadType: tt.payloadType,
				PaddingMax:  tt.paddingMax,
			}
			wrappedLeft, err := newObfsPacketConn(left, key, cfg, NewObfsState(), 1)
			if err != nil {
				t.Fatal(err)
			}
			wrappedRight, err := newObfsPacketConn(right, key, cfg, NewObfsState(), 2)
			if err != nil {
				t.Fatal(err)
			}

			payload := make([]byte, tt.payloadSize)
			for i := range payload {
				payload[i] = byte(i)
			}

			type writeResult struct {
				n   int
				err error
			}
			writeDone := make(chan writeResult, 1)
			go func() {
				n, err := wrappedLeft.WriteTo(payload, &net.UDPAddr{})
				writeDone <- writeResult{n: n, err: err}
			}()

			if err := wrappedRight.SetReadDeadline(time.Now().Add(2 * time.Second)); err != nil {
				t.Fatal(err)
			}
			dst := make([]byte, readBufSize)
			n, _, err := wrappedRight.ReadFrom(dst)
			if err != nil {
				t.Fatal(err)
			}
			if !bytes.Equal(payload, dst[:n]) {
				t.Fatalf("payload mismatch: got %d bytes, want %d", n, len(payload))
			}

			result := <-writeDone
			if result.err != nil {
				t.Fatal(result.err)
			}
			if result.n != len(payload) {
				t.Fatalf("write length: got %d, want %d", result.n, len(payload))
			}
		})
	}
}

func BenchmarkObfsWrapPacketInto(b *testing.B) {
	key := bytes.Repeat([]byte{0x5A}, wrapKeyLen)
	aead, err := newObfsAEAD(key)
	if err != nil {
		b.Fatal(err)
	}
	cfg := &ObfsConfig{
		SSRC:        0x10203040,
		PayloadType: 96,
		PaddingMax:  60,
	}
	state := NewObfsState()
	payload := make([]byte, 1280)
	dst := make([]byte, obfsWrapWireLen(len(payload), cfg))
	var nonce [12]byte
	b.ReportAllocs()
	b.SetBytes(int64(len(payload)))
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		if _, err := obfsWrapPacketInto(dst, aead, payload, cfg, state, &nonce); err != nil {
			b.Fatal(err)
		}
	}
}
