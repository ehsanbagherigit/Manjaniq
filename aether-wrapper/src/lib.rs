#![allow(non_snake_case)]
#![allow(clippy::missing_safety_doc)]

use jni::objects::{JClass, JString};
use jni::JNIEnv;
use std::ffi::{CStr, CString};
use std::os::raw::c_char;

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
    let ptr = aether::ffi::aether_job_poll(id as u64);
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherJobCancel<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
    id: i64,
) -> JString<'a> {
    let ptr = aether::ffi::aether_job_cancel(id as u64);
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherJobFree<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
    id: i64,
) -> JString<'a> {
    let ptr = aether::ffi::aether_job_free(id as u64);
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherIdentityOpen<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>, payload: JString<'a>,
) -> JString<'a> {
    let rust_payload = jstring_to_rust(&mut env, payload);
    let c_payload = match CString::new(rust_payload) {
        Ok(c) => c,
        Err(_) => {
            return env
                .new_string("{\"ok\":false,\"error\":\"payload contained null byte\"}")
                .unwrap()
        }
    };
    let ptr = unsafe { aether::ffi::aether_identity_open(c_payload.as_ptr()) };
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherIdentitySummary<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
    id: i64,
) -> JString<'a> {
    let ptr = aether::ffi::aether_identity_summary(id as u64);
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherIdentityFree<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
    id: i64,
) -> JString<'a> {
    let ptr = aether::ffi::aether_identity_free(id as u64);
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherScanStart<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
    identityId: i64, payload: JString<'a>,
) -> JString<'a> {
    let rust_payload = jstring_to_rust(&mut env, payload);
    let c_payload = match CString::new(rust_payload) {
        Ok(c) => c,
        Err(_) => {
            return env
                .new_string("{\"ok\":false,\"error\":\"payload contained null byte\"}")
                .unwrap()
        }
    };
    let ptr = unsafe { aether::ffi::aether_scan_start(identityId as u64, c_payload.as_ptr()) };
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherVerifyStart<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
    identityId: i64, payload: JString<'a>,
) -> JString<'a> {
    let rust_payload = jstring_to_rust(&mut env, payload);
    let c_payload = match CString::new(rust_payload) {
        Ok(c) => c,
        Err(_) => {
            return env
                .new_string("{\"ok\":false,\"error\":\"payload contained null byte\"}")
                .unwrap()
        }
    };
    let ptr = unsafe { aether::ffi::aether_verify_start(identityId as u64, c_payload.as_ptr()) };
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherTunnelStart<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
    identityId: i64, payload: JString<'a>,
) -> JString<'a> {
    let rust_payload = jstring_to_rust(&mut env, payload);
    let c_payload = match CString::new(rust_payload) {
        Ok(c) => c,
        Err(_) => {
            return env
                .new_string("{\"ok\":false,\"error\":\"payload contained null byte\"}")
                .unwrap()
        }
    };
    let ptr = unsafe { aether::ffi::aether_tunnel_start(identityId as u64, c_payload.as_ptr()) };
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherCoreStart<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>, argsJson: JString<'a>,
) -> JString<'a> {
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
    let ptr = aether::ffi::aether_team_code_resend(session as u64);
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherTeamCodeSubmit<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>,
    session: i64, code: JString<'a>,
) -> JString<'a> {
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
    let ptr = aether::ffi::aether_team_session_free(id as u64);
    c_string_to_jstring(&mut env, ptr)
}

#[no_mangle]
pub extern "C" fn Java_com_manjaniq_AetherBridge_nativeAetherTeamTokenSet<'a>(mut env: JNIEnv<'a>, _class: JClass<'a>, token: JString<'a>,
) -> JString<'a> {
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
    let ptr = aether::ffi::aether_team_token_clear();
    c_string_to_jstring(&mut env, ptr)
}
