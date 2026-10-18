// Package rstaspoof provides the in-repository Go engine used by the
// Manjaniq JNI bridge (native/bridge.go).
//
// IMPORTANT — scope of this implementation: the original external
// RSTA engine source (with SNI-spoofing / ClientHello fragmentation)
// was never committed to this repository: ".gitignore" excluded the
// whole "engine/" directory, so the package imported by bridge.go
// ("manjaniq/engine/rstaspoof") did not exist on GitHub and every CI
// build failed with "cannot find package". This package restores a
// buildable, honest engine with the exact lifecycle API the bridge
// expects (Config, NewEngine, Start, Stop, State, Metrics):
//
//   - Start binds LISTEN_HOST:LISTEN_PORT synchronously, so a failed
//     bind is reported to the UI as an error instead of fake success.
//   - Accepted connections are relayed byte-for-byte to
//     CONNECT_IP:CONNECT_PORT (plain TCP forwarding), with live
//     connection/byte metrics.
//   - Stop closes the listener and all relayed connections, is
//     idempotent, and the engine can be started again afterwards.
//
// The DPI-bypass behaviours named by the config fields (FAKE_SNI,
// BYPASS_METHOD, FRAGMENT_STRATEGY, ...) are accepted so the existing
// Kotlin<->Go JSON contract keeps working, but they are NOT yet
// implemented here. Porting the upstream spoofing logic into relay()
// below is the remaining engine work; it does not require any change
// to the bridge, the workflow, or the Android app.
package rstaspoof

import (
	"errors"
	"fmt"
	"io"
	"net"
	"sync"
	"sync/atomic"
	"time"
)

// State is the engine lifecycle state. The string values must match
// the Kotlin enum com.manjaniq.core.models.EngineState, which parses
// them with EngineState.valueOf(...).
type State string

const (
	StateStopped  State = "STOPPED"
	StateStarting State = "STARTING"
	StateRunning  State = "RUNNING"
	StateStopping State = "STOPPING"
	StateError    State = "ERROR"
)

// Config mirrors the JSON produced by
// NativeBridge.convertConfigToJSON on the Kotlin side.
type Config struct {
	ListenHost          string  `json:"LISTEN_HOST"`
	ListenPort          int     `json:"LISTEN_PORT"`
	ConnectIP           string  `json:"CONNECT_IP"`
	ConnectPort         int     `json:"CONNECT_PORT"`
	FakeSNI             string  `json:"FAKE_SNI"`
	BypassMethod        string  `json:"BYPASS_METHOD"`
	FragmentStrategy    string  `json:"FRAGMENT_STRATEGY"`
	FragmentDelay       float64 `json:"FRAGMENT_DELAY"`
	UseTTLTrick         bool    `json:"USE_TTL_TRICK"`
	FakeSNIMethod       string  `json:"FAKE_SNI_METHOD"`
	FakeSNIFragmentReal bool    `json:"FAKE_SNI_FRAGMENT_REAL"`
	Monitor             bool    `json:"MONITOR"`
	Quiet               bool    `json:"quiet"`
}

// Metrics is a point-in-time snapshot of engine activity. It is
// marshalled to JSON by the JNI bridge (GetMetrics).
type Metrics struct {
	State               string `json:"state"`
	ListenAddr          string `json:"listen_addr"`
	ConnectAddr         string `json:"connect_addr"`
	ConnectionsTotal    uint64 `json:"connections_total"`
	ConnectionsActive   int64  `json:"connections_active"`
	BytesClientToTarget uint64 `json:"bytes_client_to_target"`
	BytesTargetToClient uint64 `json:"bytes_target_to_client"`
	UptimeSeconds       int64  `json:"uptime_seconds"`
}

// Engine is a stoppable TCP relay. The zero value is not usable;
// construct it with NewEngine. All methods are safe for concurrent
// use (the JNI bridge additionally serialises its own calls).
type Engine struct {
	mu        sync.Mutex
	state     State
	cfg       Config
	listener  net.Listener
	conns     map[net.Conn]struct{}
	startedAt time.Time

	connsTotal  atomic.Uint64
	connsActive atomic.Int64
	bytesUp     atomic.Uint64 // client -> target
	bytesDown   atomic.Uint64 // target -> client
}

// NewEngine returns an engine in the STOPPED state.
func NewEngine() *Engine {
	return &Engine{
		state: StateStopped,
		conns: make(map[net.Conn]struct{}),
	}
}

// Start validates cfg, binds the listen socket and begins relaying
// connections in the background. It returns an error (and leaves the
// engine in ERROR state) if the config is invalid or the bind fails,
// so callers never report a running engine that is not listening.
func (e *Engine) Start(cfg Config) error {
	e.mu.Lock()
	defer e.mu.Unlock()

	if e.state == StateRunning || e.state == StateStarting {
		return errors.New("engine is already running")
	}
	if err := validate(cfg); err != nil {
		e.state = StateError
		return err
	}

	host := cfg.ListenHost
	if host == "" {
		host = "127.0.0.1"
	}
	ln, err := net.Listen("tcp", net.JoinHostPort(host, fmt.Sprint(cfg.ListenPort)))
	if err != nil {
		e.state = StateError
		return fmt.Errorf("listen %s:%d: %w", host, cfg.ListenPort, err)
	}

	e.cfg = cfg
	e.listener = ln
	e.startedAt = time.Now()
	e.state = StateRunning
	go e.acceptLoop(ln, cfg)
	return nil
}

// Stop closes the listener and every relayed connection. It is
// idempotent: stopping an already-stopped engine succeeds.
func (e *Engine) Stop() error {
	e.mu.Lock()
	defer e.mu.Unlock()

	if e.listener != nil {
		// Closing the listener makes acceptLoop return.
		_ = e.listener.Close()
		e.listener = nil
	}
	for c := range e.conns {
		_ = c.Close()
		delete(e.conns, c)
	}
	e.state = StateStopped
	return nil
}

// State reports the current lifecycle state.
func (e *Engine) State() State {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.state
}

// Metrics returns a snapshot of the engine's counters.
func (e *Engine) Metrics() Metrics {
	e.mu.Lock()
	m := Metrics{
		State:             string(e.state),
		ConnectionsTotal:  e.connsTotal.Load(),
		ConnectionsActive: e.connsActive.Load(),
		BytesClientToTarget: e.bytesUp.Load(),
		BytesTargetToClient: e.bytesDown.Load(),
	}
	if e.listener != nil {
		m.ListenAddr = e.listener.Addr().String()
	}
	if e.cfg.ConnectIP != "" {
		m.ConnectAddr = net.JoinHostPort(e.cfg.ConnectIP, fmt.Sprint(e.cfg.ConnectPort))
	}
	if e.state == StateRunning && !e.startedAt.IsZero() {
		m.UptimeSeconds = int64(time.Since(e.startedAt).Seconds())
	}
	e.mu.Unlock()
	return m
}

func validate(cfg Config) error {
	if cfg.ListenPort < 0 || cfg.ListenPort > 65535 {
		return fmt.Errorf("invalid LISTEN_PORT %d (want 0-65535)", cfg.ListenPort)
	}
	if cfg.ConnectIP == "" {
		return errors.New("CONNECT_IP must not be empty")
	}
	if cfg.ConnectPort < 1 || cfg.ConnectPort > 65535 {
		return fmt.Errorf("invalid CONNECT_PORT %d (want 1-65535)", cfg.ConnectPort)
	}
	return nil
}

func (e *Engine) acceptLoop(ln net.Listener, cfg Config) {
	for {
		client, err := ln.Accept()
		if err != nil {
			// Listener closed by Stop (or a fatal accept error).
			return
		}
		e.connsTotal.Add(1)
		e.connsActive.Add(1)
		e.track(client, true)
		go e.relay(client, cfg)
	}
}

// relay forwards one accepted connection to the configured target.
// This is the seam where the upstream SNI-spoof/fragmentation logic
// belongs; until it is ported, bytes are forwarded unchanged.
func (e *Engine) relay(client net.Conn, cfg Config) {
	defer func() {
		e.track(client, false)
		_ = client.Close()
		e.connsActive.Add(-1)
	}()

	targetAddr := net.JoinHostPort(cfg.ConnectIP, fmt.Sprint(cfg.ConnectPort))
	target, err := net.DialTimeout("tcp", targetAddr, 15*time.Second)
	if err != nil {
		return
	}
	e.track(target, true)
	defer func() {
		e.track(target, false)
		_ = target.Close()
	}()

	done := make(chan struct{}, 2)
	go func() {
		n, _ := io.Copy(target, client)
		e.bytesUp.Add(uint64(n))
		done <- struct{}{}
	}()
	go func() {
		n, _ := io.Copy(client, target)
		e.bytesDown.Add(uint64(n))
		done <- struct{}{}
	}()
	// Whichever direction ends first tears the connection down.
	<-done
	_ = client.Close()
	_ = target.Close()
	<-done
}

func (e *Engine) track(c net.Conn, add bool) {
	e.mu.Lock()
	if add {
		e.conns[c] = struct{}{}
	} else {
		delete(e.conns, c)
	}
	e.mu.Unlock()
}
