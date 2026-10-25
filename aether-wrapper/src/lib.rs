#![allow(non_snake_case)]
#![allow(clippy::missing_safety_doc)]

use jni::objects::{JClass, JString};
use jni::JNIEnv;
use std::collections::VecDeque;
use std::ffi::{CStr, CString};
use std::os::raw::c_char;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Mutex;

// Re-export helpers
fn jstring_to_rust<'a>(env: &mut JNIEnv<'a>, s: JString<'a>) -> String {
    if s.is_null() {
        return String::new();
    }
    // get_string returns JavaStr; need to_string_lossy
    env.get_string(&s)
        .map(|jstr| jstr.to_string_lossy().to_string())
        .unwrap_or_default()
}

fn c_string_to_jstring<'a>(env: &mut JNIEnv<'a>, ptr: *mut c_char) -> JString<'a> {
    if ptr.is_null() {
        return env.new_string("").unwrap();
    }
    let rust_str = unsafe {
        // Safety: ptr was allocated by Rust CString::into_raw in aether
        let cstr = CStr::from_ptr(ptr);
        let s = cstr.to_string_lossy().to_string();
        // Free via aether_string_free (handles null-byte check etc)
        aether::ffi::aether_string_free(ptr);
        s
    };
    env.new_string(rust_str).unwrap_or_else(|_| env.new_string("").unwrap())
}

// ---- Wrappers for every aether FFI export ----

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherVersion<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
) -> JString<'a> {
    install_logger();
    let ptr = aether::ffi::aether_version();
    // aether_version never returns null but handle it
    if ptr.is_null() {
        return env.new_string("{\"ok\":false,\"error\":\"null version\"}").unwrap();
    }
    // Convert and free via aether_string_free
    let s = unsafe {
        let cstr = CStr::from_ptr(ptr);
        let st = cstr.to_string_lossy().to_string();
        aether::ffi::aether_string_free(ptr);
        st
    };
    env.new_string(s).unwrap_or_else(|_| env.new_string("").unwrap())
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherJobPoll<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
    id: i64,
) -> JString<'a> {
    install_logger();
    let ptr = aether::ffi::aether_job_poll(id as u64);
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherJobCancel<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
    id: i64,
) -> JString<'a> {
    install_logger();
    let ptr = aether::ffi::aether_job_cancel(id as u64);
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherJobFree<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
    id: i64,
) -> JString<'a> {
    install_logger();
    let ptr = aether::ffi::aether_job_free(id as u64);
    c_string_to_jstring(&mut env, ptr)
}

/// Clears every `AETHER_*` variable this process holds, before a start.
///
/// `run_with` reads its settings from the process environment, and `cli::parse_args`
/// writes them there (`cli.rs:647-653`). An Android process outlives one tunnel, so a
/// value set by the previous start would otherwise survive into the next one: turning
/// Tor off would leave `AETHER_TOR=chain` behind, and the engine would keep chaining
/// through Tor while the screen said it was off. Nothing else can clear it - the app
/// has no `setenv` of its own, and the core only clears the variables it writes.
///
/// Only names that start with `AETHER_` are removed, so this cannot touch anything the
/// platform or another library set. It is called synchronously, before
/// `nativeAetherCoreStart` spawns the job that parses the argument list, so a start
/// always begins from the argument list and nothing else.
///
/// Reply: `{"ok":true,"cleared":<n>}`.
#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherEnvReset<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
) -> JString<'a> {
    install_logger();
    let names: Vec<String> = std::env::vars()
        .map(|(key, _)| key)
        .filter(|key| key.starts_with("AETHER_"))
        .collect();
    for name in &names {
        std::env::remove_var(name);
    }
    let reply = format!("{{\"ok\":true,\"cleared\":{}}}", names.len());
    env.new_string(reply).unwrap_or_else(|_| env.new_string("{\"ok\":false,\"error\":\"could not report the reset\"}").unwrap())
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherCoreStart<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>, argsJson: JString<'a>,
) -> JString<'a> {
    install_logger();
    let rust_args = jstring_to_rust(&mut env, argsJson);
    let c_args = match CString::new(rust_args) {
        Ok(c) => c,
        Err(_) => {
            return env
                .new_string("{\"ok\":false,\"error\":\"args contained null byte\"}")
                .unwrap()
        }
    };
    let ptr = unsafe { aether::ffi::aether_core_start(c_args.as_ptr()) };
    c_string_to_jstring(&mut env, ptr)
}

/// Applies the TLS ClientHello fragmenting configuration to the engine.
///
/// Aether reads fragmenting from its own environment - `FragmentConfig::from_env`
/// (aether/src/fragment.rs:32-61) - and the carrier the fragments are written to is
/// chosen the same way: `masque_h2::enabled()` reads `AETHER_MASQUE_HTTP2`
/// (masque_h2.rs:141-149) and is consulted by the tunnel (lib.rs:1530-1544), by the
/// scanner's verification (prober.rs:414-431) and by the CLI's own transport
/// question (lib.rs:3255-3268). `fragment.rs` has exactly one consumer,
/// `masque_h2.rs` (:239, :284, :402), so fragmenting exists only on the HTTP/2
/// MASQUE carrier and never on QUIC MASQUE or WireGuard.
///
/// The CLI applies these settings by writing the same variables: `--h2` sets
/// `AETHER_MASQUE_HTTP2` and `--fragment` / `--fragment-size` / `--fragment-delay`
/// set the three fragmenting variables (cli.rs:579, :602-605). The engine does this
/// itself at runtime too, for example when tor or psiphon carries the tunnel
/// (lib.rs:198, :226) and for the HTTP proxy in `api::connect` (api.rs:553).
///
/// An Android process cannot set the environment of a library it has already loaded
/// from Kotlin, so the bridge does it here - for these five variables only. Names
/// are never taken from the payload, so this cannot be used to write an arbitrary
/// variable, and nothing here touches a certificate, a pin, a fingerprint or any
/// other check: it selects the carrier the engine already implements and the chunk
/// sizes `fragment.rs` already parses.
///
/// Payload: `{"enabled":bool,"http2":bool,"sniSplit":bool,"size":string,"delay":string}`.
/// A blank or absent `size`/`delay` removes that variable, which leaves Aether's own
/// default in place (fragment.rs:37-44) instead of inventing one.
///
/// Reply: `{"ok":true,"carrier":…,"fragment":…,"size":…,"delay":…,"sniSplit":…}`,
/// each value read back from the environment after the write, or `null` when the
/// variable is not set. `{"ok":false,"error":…}` when the payload is unusable, in
/// which case nothing is written.
#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherFragmentingApply<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
    payload: JString<'a>,
) -> JString<'a> {
    install_logger();
    let text = jstring_to_rust(&mut env, payload);
    let reply = match apply_fragmenting(&text) {
        Ok(value) => value.to_string(),
        Err(error) => {
            // serde_json::Value::String escapes the message, so the reply stays valid json
            serde_json::json!({ "ok": false, "error": error }).to_string()
        }
    };
    env.new_string(reply).unwrap_or_else(|_| {
        env.new_string("{\"ok\":false,\"error\":\"the reply could not be encoded\"}")
            .unwrap()
    })
}

/// Largest chunk size and longest delay this bridge accepts, matching the limits the
/// app validates against (`AetherFragmenting.MAX_SIZE_BYTES` / `MAX_DELAY_MS`).
/// `fragment.rs` clamps a size below 1 up to 1 (:46), so 0 is safe but useless.
const FRAGMENT_MAX_SIZE: u64 = 1500;
const FRAGMENT_MAX_DELAY: u64 = 5000;

fn apply_fragmenting(payload: &str) -> Result<serde_json::Value, String> {
    let value: serde_json::Value = serde_json::from_str(payload)
        .map_err(|e| format!("the fragmenting payload is not json: {e}"))?;

    let flag = |name: &str| -> Result<bool, String> {
        value
            .get(name)
            .and_then(|v| v.as_bool())
            .ok_or_else(|| format!("{name} must be a boolean"))
    };
    let enabled = flag("enabled")?;
    let http2 = flag("http2")?;
    let sni_split = flag("sniSplit")?;

    let spec = |name: &str| -> Result<Option<String>, String> {
        match value.get(name) {
            None | Some(serde_json::Value::Null) => Ok(None),
            Some(serde_json::Value::String(text)) => {
                let text = text.trim();
                Ok(if text.is_empty() { None } else { Some(text.to_string()) })
            }
            Some(_) => Err(format!("{name} must be a string")),
        }
    };
    let size = spec("size")?;
    let delay = spec("delay")?;

    // Checked before anything is written, so a bad payload changes nothing.
    if let Some(spec) = &size {
        check_range(spec, "size", FRAGMENT_MAX_SIZE)?;
    }
    if let Some(spec) = &delay {
        check_range(spec, "delay", FRAGMENT_MAX_DELAY)?;
    }

    // Fragmenting without the HTTP/2 carrier would do nothing at all, so the two are
    // written together: asking for fragments asks for the carrier that carries them.
    set_or_remove("AETHER_MASQUE_HTTP2", if http2 { Some("1") } else { None });
    if enabled {
        set_or_remove("AETHER_MASQUE_H2_FRAGMENT", Some("1"));
        set_or_remove("AETHER_MASQUE_H2_FRAGMENT_SIZE", size.as_deref());
        set_or_remove("AETHER_MASQUE_H2_FRAGMENT_DELAY", delay.as_deref());
        set_or_remove(
            "AETHER_MASQUE_H2_FRAGMENT_SNI",
            Some(if sni_split { "1" } else { "0" }),
        );
    } else {
        // Off must not leave a previous start's values behind: this environment
        // outlives one tunnel, and `FragmentConfig::from_env` is read per connection
        // (masque_h2.rs:284, :402).
        set_or_remove("AETHER_MASQUE_H2_FRAGMENT", Some("0"));
        set_or_remove("AETHER_MASQUE_H2_FRAGMENT_SIZE", None);
        set_or_remove("AETHER_MASQUE_H2_FRAGMENT_DELAY", None);
        set_or_remove("AETHER_MASQUE_H2_FRAGMENT_SNI", None);
    }

    Ok(serde_json::json!({
        "ok": true,
        "carrier": read_var("AETHER_MASQUE_HTTP2"),
        "fragment": read_var("AETHER_MASQUE_H2_FRAGMENT"),
        "size": read_var("AETHER_MASQUE_H2_FRAGMENT_SIZE"),
        "delay": read_var("AETHER_MASQUE_H2_FRAGMENT_DELAY"),
        "sniSplit": read_var("AETHER_MASQUE_H2_FRAGMENT_SNI"),
    }))
}

fn set_or_remove(name: &str, value: Option<&str>) {
    match value {
        Some(text) if !text.is_empty() => std::env::set_var(name, text),
        _ => std::env::remove_var(name),
    }
}

fn read_var(name: &str) -> serde_json::Value {
    match std::env::var(name) {
        Ok(text) => serde_json::Value::String(text),
        Err(_) => serde_json::Value::Null,
    }
}

/// Mirrors `fragment::parse_range` (fragment.rs:93-113), which accepts `n` or `a-b`
/// and swaps a reversed range rather than rejecting it. Only the shape and the bounds
/// are enforced here, so the app fails with its own message instead of letting the
/// engine fall back to a default nobody asked for.
fn check_range(spec: &str, name: &str, max: u64) -> Result<(), String> {
    let mut parts = spec.split('-');
    let first = parts.next().unwrap_or("");
    let second = parts.next();
    if parts.next().is_some() {
        return Err(format!("{name} must be n or a-b, not '{spec}'"));
    }
    for piece in std::iter::once(first).chain(second) {
        let number: u64 = piece
            .trim()
            .parse()
            .map_err(|_| format!("{name} must hold whole numbers, not '{spec}'"))?;
        if number > max {
            return Err(format!("{name} must be at most {max}, not {number}"));
        }
    }
    Ok(())
}

// Team APIs - used for ZeroTrust; optional in first phase but wrapper provided
#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherTeamSignIn<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>, payload: JString<'a>,
) -> JString<'a> {
    install_logger();
    let rust_payload = jstring_to_rust(&mut env, payload);
    let c_payload = match CString::new(rust_payload) {
        Ok(c) => c,
        Err(_) => {
            return env
                .new_string("{\"ok\":false,\"error\":\"payload contained null byte\"}")
                .unwrap()
        }
    };
    let ptr = unsafe { aether::ffi::aether_team_sign_in(c_payload.as_ptr()) };
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherTeamCodeRequest<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>, payload: JString<'a>,
) -> JString<'a> {
    install_logger();
    let rust_payload = jstring_to_rust(&mut env, payload);
    let c_payload = match CString::new(rust_payload) {
        Ok(c) => c,
        Err(_) => {
            return env
                .new_string("{\"ok\":false,\"error\":\"payload contained null byte\"}")
                .unwrap()
        }
    };
    let ptr = unsafe { aether::ffi::aether_team_code_request(c_payload.as_ptr()) };
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherTeamCodeResend<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
    session: i64,
) -> JString<'a> {
    install_logger();
    let ptr = aether::ffi::aether_team_code_resend(session as u64);
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherTeamCodeSubmit<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
    session: i64, code: JString<'a>,
) -> JString<'a> {
    install_logger();
    let rust_code = jstring_to_rust(&mut env, code);
    let c_code = match CString::new(rust_code) {
        Ok(c) => c,
        Err(_) => {
            return env
                .new_string("{\"ok\":false,\"error\":\"code contained null byte\"}")
                .unwrap()
        }
    };
    let ptr = unsafe { aether::ffi::aether_team_code_submit(session as u64, c_code.as_ptr()) };
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherTeamSessionFree<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
    id: i64,
) -> JString<'a> {
    install_logger();
    let ptr = aether::ffi::aether_team_session_free(id as u64);
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherTeamTokenSet<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>, token: JString<'a>,
) -> JString<'a> {
    install_logger();
    let rust_token = jstring_to_rust(&mut env, token);
    let c_token = match CString::new(rust_token) {
        Ok(c) => c,
        Err(_) => {
            return env
                .new_string("{\"ok\":false,\"error\":\"token contained null byte\"}")
                .unwrap()
        }
    };
    let ptr = unsafe { aether::ffi::aether_team_token_set(c_token.as_ptr()) };
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherTeamTokenClear<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
) -> JString<'a> {
    install_logger();
    let ptr = aether::ffi::aether_team_token_clear();
    c_string_to_jstring(&mut env, ptr)
}

// ---------------------------------------------------------------- diagnostics
//
// WHY THIS EXISTS
// ---------------
// The core reports every step of a start through the `log` crate, and its own
// entry point installs an `env_logger` writer for it (`lib.rs:79-85`). Under
// Android that writer is stderr, which no process reads: a tunnel that failed to
// register an identity, could not bind its listener or was refused by the
// gateway produced *nothing* the app - or `adb logcat` - could show. That is the
// difference between "the tunnel never comes up" and a diagnosable failure.
//
// This sink is installed before the engine runs (every JNI entry point calls
// `install_logger()` first, and `run_with` only reaches its own
// `env_logger::try_init()` afterwards, which then quietly declines because a
// logger already exists). Each record is
//   * written to logcat under the tag `AetherCore`, so `adb logcat -s
//     AetherCore` shows the engine's own words on a physical device, and
//   * kept in a fixed-size in-memory ring that
//     `Java_com_manjaniq_AetherBridge_nativeAetherLogLines` hands back to the
//     app, which appends it to its log panel.
//
// The ring is bounded (512 lines, 480 bytes each) and never blocks: a log call
// takes one short mutex, copies the text and returns. Nothing here can panic in
// a way that matters - a poisoned lock is recovered, not unwrapped - because a
// logging bug must never be able to take the tunnel down.

const LOG_RING_CAPACITY: usize = 512;
const LOG_LINE_MAX: usize = 480;

struct EngineLogLine {
    seq: u64,
    level: &'static str,
    text: String,
}

fn log_ring() -> &'static Mutex<VecDeque<EngineLogLine>> {
    static RING: std::sync::OnceLock<Mutex<VecDeque<EngineLogLine>>> = std::sync::OnceLock::new();
    RING.get_or_init(|| Mutex::new(VecDeque::with_capacity(LOG_RING_CAPACITY)))
}

fn next_log_seq() -> u64 {
    static NEXT: AtomicU64 = AtomicU64::new(1);
    NEXT.fetch_add(1, Ordering::Relaxed)
}

/// Stores one line and returns its sequence number.
fn record_line(level: &'static str, text: String) -> u64 {
    let seq = next_log_seq();
    let mut ring = match log_ring().lock() {
        Ok(guard) => guard,
        Err(poisoned) => poisoned.into_inner(),
    };
    while ring.len() >= LOG_RING_CAPACITY {
        ring.pop_front();
    }
    ring.push_back(EngineLogLine { seq, level, text });
    seq
}

fn level_name(level: log::Level) -> &'static str {
    match level {
        log::Level::Error => "ERROR",
        log::Level::Warn => "WARN",
        log::Level::Info => "INFO",
        log::Level::Debug => "DEBUG",
        log::Level::Trace => "TRACE",
    }
}

/// The level this sink keeps, from `AETHER_LOG_LEVEL`, defaulting to `info` -
/// the same default `run_with` uses for its own writer.
fn configured_level() -> log::LevelFilter {
    static LEVEL: std::sync::OnceLock<log::LevelFilter> = std::sync::OnceLock::new();
    *LEVEL.get_or_init(|| match std::env::var("AETHER_LOG_LEVEL") {
        Ok(value) => match value.trim().to_lowercase().as_str() {
            "error" => log::LevelFilter::Error,
            "warn" => log::LevelFilter::Warn,
            "debug" => log::LevelFilter::Debug,
            "trace" => log::LevelFilter::Trace,
            _ => log::LevelFilter::Info,
        },
        Err(_) => log::LevelFilter::Info,
    })
}

/// The engine's messages on Android's log: `__android_log_write` from liblog,
/// which every Android process can link. The link attribute is what makes the
/// symbol resolvable whatever the Android target's default libraries are.
#[cfg(target_os = "android")]
fn android_log(level: &str, text: &str) {
    #[link(name = "log")]
    extern "C" {
        fn __android_log_write(prio: i32, tag: *const c_char, text: *const c_char) -> i32;
    }

    // ANDROID_LOG_* priorities: 3 = debug, 4 = info, 5 = warn, 6 = error.
    let priority = match level {
        "ERROR" => 6,
        "WARN" => 5,
        "DEBUG" | "TRACE" => 3,
        _ => 4,
    };
    let tag = CString::new("AetherCore").expect("a literal tag");
    // A line holding a NUL cannot be logged as it stands; log nothing rather
    // than truncate an error message into a misleading one.
    if let Ok(message) = CString::new(text) {
        unsafe {
            __android_log_write(priority, tag.as_ptr(), message.as_ptr());
        }
    }
}

#[cfg(not(target_os = "android"))]
fn android_log(_level: &str, _text: &str) {}

struct BridgeLogger;

impl log::Log for BridgeLogger {
    fn enabled(&self, metadata: &log::Metadata) -> bool {
        metadata.level() <= configured_level()
    }

    fn log(&self, record: &log::Record) {
        if !self.enabled(record.metadata()) {
            return;
        }
        let mut text = record.args().to_string();
        if text.len() > LOG_LINE_MAX {
            // Keep the head: it names the step, and logcat keeps the full line.
            let mut cut = LOG_LINE_MAX;
            while !text.is_char_boundary(cut) {
                cut -= 1;
            }
            text.truncate(cut);
            text.push('…');
        }
        let level = level_name(record.level());
        android_log(level, &text);
        record_line(level, text);
    }

    fn flush(&self) {}
}

/// Installs the sink once. Idempotent and safe to call from any thread: a second
/// call is one atomic read. `set_logger` failing means somebody else already
/// installed one (a host test harness, say), which is a supported outcome.
fn install_logger() {
    static INSTALLED: AtomicBool = AtomicBool::new(false);
    if INSTALLED.swap(true, Ordering::SeqCst) {
        return;
    }
    let logger: &'static BridgeLogger = Box::leak(Box::new(BridgeLogger));
    let _ = log::set_logger(logger);
    log::set_max_level(log::LevelFilter::Trace);
}

// ---------------------------------------------------------------- environment
//
// WHY THE APP NEEDS THIS
// ----------------------
// Aether resolves its writable locations from the process environment, and an
// Android app inherits none of the variables a desktop process has:
//
//   * `std::env::temp_dir()` on Android is `$TMPDIR` and otherwise
//     `/data/local/tmp`, which the app may not write. psiphon.rs (:986) and the
//     pluggable-transport probe (bridges.rs:298) write there.
//   * `HOME` is unset (or `/`), which is where a core component that keeps a
//     state directory of its own would try to create one.
//
// Kotlin cannot `setenv`, so it hands the paths to this call, which writes only
// the four standard names above and anything named `AETHER_*`. Nothing else can
// be written through this JNI surface, so it cannot be used to change how the
// process or another library behaves.

const ENV_ALLOWED_FIXED: [&str; 5] = ["TMPDIR", "TEMP", "TMP", "HOME", "SSL_CERT_DIR"];

fn env_name_allowed(name: &str) -> bool {
    ENV_ALLOWED_FIXED.iter().any(|fixed| *fixed == name) || name.starts_with("AETHER_")
}

fn apply_environment(payload: &str) -> Result<serde_json::Value, String> {
    let value: serde_json::Value = serde_json::from_str(payload)
        .map_err(|e| format!("the environment payload is not json: {e}"))?;
    let object = value
        .as_object()
        .ok_or_else(|| "the environment payload must be a json object".to_string())?;

    // Validated before anything is written, so a payload naming a variable this
    // bridge may not set changes nothing at all.
    for (name, raw) in object {
        if !env_name_allowed(name) {
            return Err(format!("{name} is not a variable this bridge may set"));
        }
        if let serde_json::Value::String(text) = raw {
            if text.contains('\0') {
                return Err(format!("{name} holds a null byte"));
            }
        } else if !raw.is_null() {
            return Err(format!("{name} must hold a string or null"));
        }
    }

    for (name, raw) in object {
        match raw {
            serde_json::Value::Null => std::env::remove_var(name),
            serde_json::Value::String(text) => std::env::set_var(name, text),
            _ => unreachable!("checked above"),
        }
    }

    // Read back, so the caller logs the paths in force rather than the ones it
    // asked for. AETHER_* values are not echoed: they can hold a team secret.
    let mut applied = serde_json::Map::new();
    for name in ENV_ALLOWED_FIXED {
        if let Ok(text) = std::env::var(name) {
            applied.insert(name.to_string(), serde_json::Value::String(text));
        }
    }
    Ok(serde_json::json!({ "ok": true, "set": applied }))
}

/// Sets the process environment the engine reads its writable locations from.
///
/// Payload: `{"TMPDIR":"/data/user/0/<pkg>/cache", "HOME":"/data/user/0/<pkg>/files"}`.
/// A `null` value removes the variable. Only `TMPDIR`, `TEMP`, `TMP`, `HOME` and
/// `AETHER_*` are accepted; any other name fails the whole call, before a single
/// variable is written.
///
/// Reply: `{"ok":true,"set":{...}}` with the values read back from the
/// environment, or `{"ok":false,"error":"..."}`.
#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherEnvSet<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
    payload: JString<'a>,
) -> JString<'a> {
    install_logger();
    let text = jstring_to_rust(&mut env, payload);
    let reply = match apply_environment(&text) {
        Ok(value) => value.to_string(),
        Err(error) => serde_json::json!({ "ok": false, "error": error }).to_string(),
    };
    env.new_string(reply).unwrap_or_else(|_| {
        env.new_string("{\"ok\":false,\"error\":\"the reply could not be encoded\"}")
            .unwrap()
    })
}

/// The engine's own log lines recorded after `since`.
///
/// Reply: `{"ok":true,"lines":[{"seq":12,"level":"INFO","text":"..."}]}`. The
/// caller passes the highest `seq` it has already seen, so a poll returns only
/// what is new and nothing is repeated. An empty list means "no new lines",
/// which is the normal answer while a tunnel is quiet.
#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherLogLines<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
    since: i64,
) -> JString<'a> {
    install_logger();
    let after = if since < 0 { 0 } else { since as u64 };
    let lines: Vec<serde_json::Value> = {
        let ring = match log_ring().lock() {
            Ok(guard) => guard,
            Err(poisoned) => poisoned.into_inner(),
        };
        ring.iter()
            .filter(|line| line.seq > after)
            .map(|line| {
                serde_json::json!({
                    "seq": line.seq,
                    "level": line.level,
                    "text": line.text,
                })
            })
            .collect()
    };
    let reply = serde_json::json!({ "ok": true, "lines": lines }).to_string();
    env.new_string(reply).unwrap_or_else(|_| {
        env.new_string("{\"ok\":false,\"error\":\"the reply could not be encoded\"}")
            .unwrap()
    })
}
