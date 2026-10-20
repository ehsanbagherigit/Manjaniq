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
