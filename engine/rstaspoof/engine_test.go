package rstaspoof

import (
	"bytes"
	"io"
	"net"
	"testing"
	"time"
)

// These tests exercise the GENUINE engine logic (ClientHello
// building/parsing, fragmentation, and a full proxy pass) on the
// CI host. They do not — and cannot — prove DPI bypass on a live
// network; that needs a device on a filtering network.

// mustFinish runs a lifecycle call and fails the test, naming the
// call, instead of letting a wedged lifecycle hang the whole test
// binary until the go test timeout (observed once in CI).
func mustFinish(t *testing.T, name string, fn func() error) error {
	t.Helper()
	done := make(chan error, 1)
	go func() { done <- fn() }()
	select {
	case err := <-done:
		return err
	case <-time.After(20 * time.Second):
		t.Fatalf("%s did not finish within 20s (possible deadlock)", name)
		return nil // unreachable
	}
}

func TestFragmentStrategiesReassemble(t *testing.T) {
	hello := ClientHelloBuilder{}.BuildClientHello("real.example.com", nil, nil, nil, 517)
	if len(hello) < 100 {
		t.Fatalf("built ClientHello suspiciously small: %d bytes", len(hello))
	}
	eng := FragmentEngine{}
	for _, strategy := range []string{"sni_split", "half", "multi", "none"} {
		parts := eng.Fragment(hello, strategy)
		var joined []byte
		for _, p := range parts {
			joined = append(joined, p...)
		}
		if !bytes.Equal(joined, hello) {
			t.Errorf("strategy %s: reassembled bytes differ from original", strategy)
		}
		if strategy != "none" && len(parts) < 2 {
			t.Errorf("strategy %s: expected >=2 fragments, got %d", strategy, len(parts))
		}
	}
	// tls_record_frag wraps the handshake in two TLS records.
	parts := eng.Fragment(hello, "tls_record_frag")
	if len(parts) != 2 {
		t.Fatalf("tls_record_frag: want 2 records, got %d", len(parts))
	}
	var payload []byte
	for _, p := range parts {
		if len(p) < 6 || p[0] != 0x16 {
			t.Fatalf("tls_record_frag: record header missing (len=%d first=%#x)", len(p), p[0])
		}
		payload = append(payload, p[5:]...)
	}
	if !bytes.Equal(payload, hello[5:]) {
		t.Error("tls_record_frag: record payloads do not reassemble to the handshake")
	}
}

func TestParseBuiltClientHello(t *testing.T) {
	hello := ClientHelloBuilder{}.BuildClientHello("blocked.example.org", nil, nil, nil, 517)
	parsed := ClientHelloBuilder{}.ParseClientHello(hello)
	if parsed["handshake_type"] != "ClientHello" {
		t.Fatalf("handshake_type = %v, want ClientHello", parsed["handshake_type"])
	}
	if parsed["sni"] != "blocked.example.org" {
		t.Fatalf("parsed SNI = %v, want blocked.example.org", parsed["sni"])
	}
}

func startEcho(t *testing.T) int {
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
			go func(c net.Conn) { _, _ = io.Copy(c, c); _ = c.Close() }(c)
		}
	}()
	return ln.Addr().(*net.TCPAddr).Port
}

func TestEngineEndToEndFragment(t *testing.T) {
	ClearLogs()
	engine := NewEngine()
	cfg := Config{
		ListenHost:       "127.0.0.1",
		ListenPort:       0, // ephemeral; actual addr via Metrics
		ConnectIP:        "127.0.0.1",
		ConnectPort:      startEcho(t),
		FakeSNI:          "cdnjs.cloudflare.com",
		BypassMethod:     "fragment",
		FragmentStrategy: "sni_split",
		FragmentDelay:    0.01,
		FakeSNIMethod:    "prefix_fake",
		Monitor:          true,
	}
	if err := mustFinish(t, "Start", func() error { return engine.Start(cfg) }); err != nil {
		t.Fatalf("Start: %v", err)
	}
	if got := engine.State(); got != StateRunning {
		t.Fatalf("state = %v, want RUNNING", got)
	}
	if err := mustFinish(t, "second Start", func() error { return engine.Start(cfg) }); err == nil {
		t.Fatal("second Start while running must fail")
	}

	addr := engine.Metrics().ListenAddr
	if addr == "" {
		t.Fatal("Metrics().ListenAddr empty while running")
	}
	conn, err := net.DialTimeout("tcp", addr, 5*time.Second)
	if err != nil {
		t.Fatalf("dial proxy: %v", err)
	}
	_ = conn.SetDeadline(time.Now().Add(10 * time.Second))
	hello := ClientHelloBuilder{}.BuildClientHello("real.example.com", nil, nil, nil, 517)
	if _, err := conn.Write(hello); err != nil {
		t.Fatalf("write hello: %v", err)
	}
	got := make([]byte, len(hello))
	if _, err := io.ReadFull(conn, got); err != nil {
		t.Fatalf("read echo through proxy: %v", err)
	}
	if !bytes.Equal(got, hello) {
		t.Fatal("bytes through the genuine fragment proxy differ from what was sent")
	}
	_ = conn.Close()

	if len(engine.Logs()) == 0 {
		t.Fatal("expected engine log lines (banner/monitor) in the ring")
	}
	if m := engine.Metrics(); m.TotalConns < 1 {
		t.Fatalf("Metrics().TotalConns = %d, want >= 1", m.TotalConns)
	}

	if err := mustFinish(t, "Stop", func() error { return engine.Stop() }); err != nil {
		t.Fatalf("Stop: %v", err)
	}
	if got := engine.State(); got != StateStopped {
		t.Fatalf("state after Stop = %v, want STOPPED", got)
	}
	// Restart must work (Settings screen relies on it).
	if err := mustFinish(t, "restart Start", func() error { return engine.Start(cfg) }); err != nil {
		t.Fatalf("restart: %v", err)
	}
	if err := mustFinish(t, "restart Stop", func() error { return engine.Stop() }); err != nil {
		t.Fatalf("restart Stop: %v", err)
	}
}

func TestEngineRejectsBadConfig(t *testing.T) {
	engine := NewEngine()
	cfg := Config{ConnectPort: 0, FakeSNI: "x", BypassMethod: "fragment"}
	if err := engine.Start(cfg); err == nil {
		t.Fatal("ConnectPort=0 must be rejected")
	}
	if got := engine.State(); got == StateRunning {
		t.Fatal("failed Start must not report RUNNING")
	}
	cfg.ConnectPort = 443
	cfg.ConnectIP = "127.0.0.1"
	cfg.BypassMethod = "teleport"
	if err := engine.Start(cfg); err == nil {
		t.Fatal("unknown bypass method must be rejected, not silently replaced")
	}
}
