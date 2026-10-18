// engine.go — Manjaniq integration layer around the genuine RSTA
// engine in rstaspoof.go.
//
// The genuine file is a CLI program at heart: it expects stdout,
// signals and an infinite accept loop. This file adds the small
// embeddable surface the Android JNI bridge (native/bridge.go)
// needs — Config, Engine Start/Stop/State, Metrics, and a log ring
// that captures the engine's own diagnostic output (its monitor
// events, banner and session stats) for the Android UI. None of the
// networking, TLS or bypass logic lives here; Start() wires the
// user's config through the genuine StrategyFactory, PacketMonitor
// and ProxyServer exactly like the original main() did.
package rstaspoof

import (
	"errors"
	"fmt"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// ------------------------------------------------------------------
// Log sink: where enginePrintf/enginePrintln output lands.
// ------------------------------------------------------------------

// LogLevel classifies one log line for the UI.
type LogLevel string

const (
	LevelInfo  LogLevel = "INFO"
	LevelOK    LogLevel = "OK"
	LevelWarn  LogLevel = "WARN"
	LevelError LogLevel = "ERROR"
)

// LogEntry is one captured engine log line (JSON for the UI).
type LogEntry struct {
	Seq     int64  `json:"seq"`
	TimeMs  int64  `json:"time_ms"`
	Level   string `json:"level"`
	Message string `json:"message"`
}

const maxLogEntries = 500

var (
	// logMu serialises PacketMonitor.printEvent against the sink
	// (held in Emit) so one event's lines keep its level.
	logMu sync.Mutex
	// currentLevelCode is the level enginePrintf assumes right
	// now; atomic so printers outside Emit never race with it.
	currentLevelCode atomic.Int32

	sinkMu      sync.Mutex
	sinkEntries []LogEntry
	sinkSeq     int64
)

func levelCode(l LogLevel) int32 {
	switch l {
	case LevelOK:
		return 1
	case LevelWarn:
		return 2
	case LevelError:
		return 3
	}
	return 0
}

func codeLevel(c int32) LogLevel {
	switch c {
	case 1:
		return LevelOK
	case 2:
		return LevelWarn
	case 3:
		return LevelError
	}
	return LevelInfo
}

func setCurrentLevel(l LogLevel) { currentLevelCode.Store(levelCode(l)) }

func levelForEvent(ev string) LogLevel {
	switch ev {
	case EvConnError:
		return LevelError
	case EvDPIBlocked:
		return LevelWarn
	case EvServerResponse, EvDPIBypassOK:
		return LevelOK
	}
	return LevelInfo
}

func sinkAppend(level LogLevel, msg string) {
	// One Printf call can contain several lines (banner, stats);
	// the UI renders one entry per line, blank lines dropped.
	for _, line := range strings.Split(msg, "\n") {
		line = strings.TrimRight(line, " \t\r")
		if strings.TrimSpace(line) == "" {
			continue
		}
		sinkMu.Lock()
		sinkSeq++
		sinkEntries = append(sinkEntries, LogEntry{
			Seq:     sinkSeq,
			TimeMs:  time.Now().UnixMilli(),
			Level:   string(level),
			Message: line,
		})
		if len(sinkEntries) > maxLogEntries {
			sinkEntries = sinkEntries[len(sinkEntries)-maxLogEntries:]
		}
		sinkMu.Unlock()
	}
}

// enginePrintf/enginePrintln replace fmt.Print* in the genuine
// code: stdout of an Android app process is discarded, so engine
// diagnostics go to the log ring instead.
func enginePrintf(format string, args ...interface{}) {
	sinkAppend(codeLevel(currentLevelCode.Load()), fmt.Sprintf(format, args...))
}

func enginePrintln(args ...interface{}) {
	sinkAppend(codeLevel(currentLevelCode.Load()), fmt.Sprintln(args...))
}

// sinkWriter adapts the sink to io.Writer for *log.Logger.
type sinkWriter struct{ level LogLevel }

func (w sinkWriter) Write(p []byte) (int, error) {
	sinkAppend(w.level, string(p))
	return len(p), nil
}

// Logs returns a copy of the buffered log lines (oldest first).
func Logs() []LogEntry {
	sinkMu.Lock()
	defer sinkMu.Unlock()
	out := make([]LogEntry, len(sinkEntries))
	copy(out, sinkEntries)
	return out
}

// ClearLogs empties the ring. Sequence numbers keep increasing so
// UI pollers can tell post-clear lines from stale ones.
func ClearLogs() {
	sinkMu.Lock()
	sinkEntries = nil
	sinkMu.Unlock()
}

// ------------------------------------------------------------------
// Config / state / metrics — the JNI bridge contract.
// ------------------------------------------------------------------

// State is the engine lifecycle state; values match the Kotlin
// enum com.manjaniq.core.models.EngineState.
type State string

const (
	StateStopped  State = "STOPPED"
	StateStarting State = "STARTING"
	StateRunning  State = "RUNNING"
	StateStopping State = "STOPPING"
	StateError    State = "ERROR"
)

// Config mirrors the JSON the Kotlin side sends (the same keys the
// genuine config.json / defaultConfig use).
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

// toMap renders the struct as the map the genuine StrategyFactory
// and defaultConfig machinery consume (numbers as float64, exactly
// like a JSON-decoded config file).
func (c Config) toMap() map[string]interface{} {
	return map[string]interface{}{
		"LISTEN_HOST":            c.ListenHost,
		"LISTEN_PORT":            float64(c.ListenPort),
		"CONNECT_IP":             c.ConnectIP,
		"CONNECT_PORT":           float64(c.ConnectPort),
		"FAKE_SNI":               c.FakeSNI,
		"BYPASS_METHOD":          c.BypassMethod,
		"FRAGMENT_STRATEGY":      c.FragmentStrategy,
		"FRAGMENT_DELAY":         c.FragmentDelay,
		"USE_TTL_TRICK":          c.UseTTLTrick,
		"FAKE_SNI_METHOD":        c.FakeSNIMethod,
		"FAKE_SNI_FRAGMENT_REAL": c.FakeSNIFragmentReal,
		"MONITOR":                c.Monitor,
		"quiet":                  c.Quiet,
	}
}

func validateConfig(c Config) error {
	if c.ListenPort < 0 || c.ListenPort > 65535 {
		return fmt.Errorf("invalid LISTEN_PORT %d (want 0-65535)", c.ListenPort)
	}
	if strings.TrimSpace(c.ConnectIP) == "" {
		return errors.New("CONNECT_IP must not be empty")
	}
	if c.ConnectPort < 1 || c.ConnectPort > 65535 {
		return fmt.Errorf("invalid CONNECT_PORT %d (want 1-65535)", c.ConnectPort)
	}
	if strings.TrimSpace(c.FakeSNI) == "" || strings.ContainsAny(c.FakeSNI, " /:") {
		return fmt.Errorf("invalid FAKE_SNI %q", c.FakeSNI)
	}
	switch strings.ToLower(c.BypassMethod) {
	case "fragment", "fake_sni", "combined":
	default:
		return fmt.Errorf("unknown BYPASS_METHOD %q (want fragment, fake_sni or combined)", c.BypassMethod)
	}
	switch c.FragmentStrategy {
	case "sni_split", "half", "multi", "tls_record_frag", "none", "":
	default:
		return fmt.Errorf("unknown FRAGMENT_STRATEGY %q (want sni_split, half, multi or tls_record_frag)", c.FragmentStrategy)
	}
	if c.FragmentDelay < 0 || c.FragmentDelay > 5 {
		return fmt.Errorf("invalid FRAGMENT_DELAY %v (want 0-5 seconds)", c.FragmentDelay)
	}
	return nil
}

// Metrics is a JSON snapshot sourced from the genuine
// PacketMonitor counters (plus live socket counts).
type Metrics struct {
	State               string `json:"state"`
	Version             string `json:"version"`
	ListenAddr          string `json:"listen_addr"`
	ConnectAddr         string `json:"connect_addr"`
	Method              string `json:"method"`
	FragmentStrategy    string `json:"fragment_strategy"`
	FakeSNI             string `json:"fake_sni"`
	ActiveConns         int    `json:"active_conns"`
	TotalConns          int64  `json:"total_conns"`
	BypassOK            int64  `json:"bypass_ok"`
	Blocked             int64  `json:"blocked"`
	BytesClientToTarget int64  `json:"bytes_c2s"`
	BytesTargetToClient int64  `json:"bytes_s2c"`
	UptimeSeconds       int64  `json:"uptime_seconds"`
}

// Engine drives one genuine ProxyServer. Construct with NewEngine;
// methods are safe for concurrent use.
type Engine struct {
	mu        sync.Mutex
	state     State
	cfg       Config
	server    *ProxyServer
	mon       *PacketMonitor
	startedAt time.Time
	serveDone chan struct{}
}

func NewEngine() *Engine { return &Engine{state: StateStopped} }

// Start validates the config, binds the listen socket (a bind
// failure is returned as an error, never fake success), then runs
// the genuine proxy in the background.
func (e *Engine) Start(cfg Config) error {
	e.mu.Lock()
	defer e.mu.Unlock()

	if e.state == StateRunning || e.state == StateStarting || e.state == StateStopping {
		return fmt.Errorf("engine is already %s", strings.ToLower(string(e.state)))
	}
	e.state = StateStarting

	if err := validateConfig(cfg); err != nil {
		e.state = StateError
		sinkAppend(LevelError, "Invalid configuration: "+err.Error())
		return err
	}

	listenHost := cfg.ListenHost
	if listenHost == "" {
		listenHost = "0.0.0.0" // genuine defaultConfig value
	}
	// The genuine main() resolved the target host to an IP before
	// dialling; keep that behaviour (accepts hostnames in the UI).
	resolvedTarget := resolveHost(cfg.ConnectIP)

	// Monitoring exactly as in the genuine main(): event output is
	// live only when MONITOR is on and quiet mode is off; counters
	// are collected either way.
	mon := InitMonitor(cfg.Quiet || !cfg.Monitor)
	strategy := buildStrategy(cfg.toMap())
	// interfaceIP "": main() pinned the source address via a UDP
	// probe; on Android (Wi-Fi/cellular switching) a stale pin
	// breaks dialling, so the kernel picks the source instead.
	// All forwarding/bypass behaviour is unchanged.
	server := NewProxyServer(listenHost, cfg.ListenPort, resolvedTarget,
		cfg.ConnectPort, cfg.FakeSNI, strategy, "")

	if err := server.Listen(); err != nil {
		e.state = StateError
		sinkAppend(LevelError, fmt.Sprintf("Failed to listen on %s:%d: %v", listenHost, cfg.ListenPort, err))
		return fmt.Errorf("listen %s:%d: %w", listenHost, cfg.ListenPort, err)
	}

	done := make(chan struct{})
	e.server = server
	e.mon = mon
	e.cfg = cfg
	e.startedAt = time.Now()
	e.serveDone = done
	e.state = StateRunning
	go func() {
		err := server.Serve()
		e.mu.Lock()
		if err != nil && e.state == StateRunning {
			e.state = StateError
			sinkAppend(LevelError, "Engine stopped unexpectedly: "+err.Error())
		}
		close(done)
		e.mu.Unlock()
	}()

	sinkAppend(LevelOK, fmt.Sprintf(
		"Engine started: %s → %s | method=%s strategy=%s fake SNI=%s ttl=%v",
		server.Addr(), server.connectPort, cfg.BypassMethod,
		cfg.FragmentStrategy, cfg.FakeSNI, cfg.UseTTLTrick))
	return nil
}

// Stop ends accepting, closes in-flight connections, prints the
// genuine session stats to the log, and confirms STOPPED only
// after the serve loop has exited. Idempotent.
func (e *Engine) Stop() error {
	e.mu.Lock()
	server := e.server
	done := e.serveDone
	mon := e.mon
	if server == nil {
		e.state = StateStopped
		e.mu.Unlock()
		return nil
	}
	e.state = StateStopping
	e.mu.Unlock()

	server.Stop()
	if done != nil {
		<-done
	}
	if mon != nil {
		mon.PrintStats()
	}

	e.mu.Lock()
	e.server = nil
	e.serveDone = nil
	e.state = StateStopped
	sinkAppend(LevelInfo, "Engine stopped")
	e.mu.Unlock()
	return nil
}

// State reports the current lifecycle state.
func (e *Engine) State() State {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.state
}

// Metrics snapshots the genuine monitor counters.
func (e *Engine) Metrics() Metrics {
	e.mu.Lock()
	m := Metrics{
		State:            string(e.state),
		Version:          Version,
		Method:           e.cfg.BypassMethod,
		FragmentStrategy: e.cfg.FragmentStrategy,
		FakeSNI:          e.cfg.FakeSNI,
	}
	server := e.server
	mon := e.mon
	if e.state == StateRunning && !e.startedAt.IsZero() {
		m.UptimeSeconds = int64(time.Since(e.startedAt).Seconds())
	}
	e.mu.Unlock()

	if server != nil {
		m.ListenAddr = server.Addr()
		m.ConnectAddr = server.connectPort
		m.ActiveConns = server.ActiveClients()
	}
	if mon != nil {
		m.TotalConns = atomic.LoadInt64(&mon.counters.totalConns)
		m.BypassOK = atomic.LoadInt64(&mon.counters.totalOK)
		m.Blocked = atomic.LoadInt64(&mon.counters.totalBlocked)
		m.BytesClientToTarget = atomic.LoadInt64(&mon.counters.totalC2S)
		m.BytesTargetToClient = atomic.LoadInt64(&mon.counters.totalS2C)
	}
	return m
}

// Logs returns the buffered engine log lines.
func (e *Engine) Logs() []LogEntry { return Logs() }

// ClearLogs empties the log buffer.
func (e *Engine) ClearLogs() { ClearLogs() }
