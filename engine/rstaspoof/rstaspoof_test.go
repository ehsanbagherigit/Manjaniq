package rstaspoof

import (
	"io"
	"net"
	"testing"
	"time"
)

// startEchoServer starts a throwaway TCP echo server and returns its port.
func startEchoServer(t *testing.T) int {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("echo listen: %v", err)
	}
	t.Cleanup(func() { _ = ln.Close() })
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go func(c net.Conn) {
				_, _ = io.Copy(c, c)
				_ = c.Close()
			}(c)
		}
	}()
	return ln.Addr().(*net.TCPAddr).Port
}

func testConfig(echoPort int) Config {
	return Config{
		ListenHost:  "127.0.0.1",
		ListenPort:  0, // ephemeral; actual address via Metrics
		ConnectIP:   "127.0.0.1",
		ConnectPort: echoPort,
	}
}

func TestEngineRelayAndLifecycle(t *testing.T) {
	echoPort := startEchoServer(t)
	e := NewEngine()
	if got := e.State(); got != StateStopped {
		t.Fatalf("new engine state = %v, want STOPPED", got)
	}

	if err := e.Start(testConfig(echoPort)); err != nil {
		t.Fatalf("Start: %v", err)
	}
	if got := e.State(); got != StateRunning {
		t.Fatalf("state after Start = %v, want RUNNING", got)
	}
	if err := e.Start(testConfig(echoPort)); err == nil {
		t.Fatal("second Start while running: want error, got nil")
	}

	addr := e.Metrics().ListenAddr
	if addr == "" {
		t.Fatal("Metrics().ListenAddr is empty while running")
	}
	conn, err := net.DialTimeout("tcp", addr, 5*time.Second)
	if err != nil {
		t.Fatalf("dial engine: %v", err)
	}
	_ = conn.SetDeadline(time.Now().Add(5 * time.Second))
	if _, err := conn.Write([]byte("hello")); err != nil {
		t.Fatalf("write: %v", err)
	}
	buf := make([]byte, 5)
	if _, err := io.ReadFull(conn, buf); err != nil {
		t.Fatalf("read echo: %v", err)
	}
	if string(buf) != "hello" {
		t.Fatalf("echo = %q, want %q", buf, "hello")
	}
	_ = conn.Close()

	if err := e.Stop(); err != nil {
		t.Fatalf("Stop: %v", err)
	}
	if got := e.State(); got != StateStopped {
		t.Fatalf("state after Stop = %v, want STOPPED", got)
	}
	if err := e.Stop(); err != nil {
		t.Fatalf("second Stop must be idempotent: %v", err)
	}

	// Restart after stop must work (bridge reuses one Engine).
	if err := e.Start(testConfig(echoPort)); err != nil {
		t.Fatalf("restart Start: %v", err)
	}
	if err := e.Stop(); err != nil {
		t.Fatalf("restart Stop: %v", err)
	}
}

func TestEngineRejectsInvalidConfig(t *testing.T) {
	e := NewEngine()
	cfg := testConfig(443)
	cfg.ConnectPort = 0
	if err := e.Start(cfg); err == nil {
		t.Fatal("Start with CONNECT_PORT=0: want error, got nil")
	}
	if got := e.State(); got == StateRunning {
		t.Fatal("engine must not report RUNNING after a failed Start")
	}
}
