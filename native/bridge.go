package main

/*
#include <jni.h>
#include <stdlib.h>
#include <string.h>

// Wrapper for creating a UTF string
jstring NewStringUTF_wrapper(JNIEnv* env, const char* str);

// Wrapper for getting UTF chars from a jstring
const char* GetStringUTFChars_wrapper(JNIEnv* env, jstring str);

// Wrapper for releasing UTF chars
void ReleaseStringUTFChars_wrapper(JNIEnv* env, jstring str, const char* chars);

// Helper to check if a jstring is NULL
int jstringIsNil(JNIEnv* env, jstring str);
*/
import "C"
import (
	"encoding/json"
	"fmt"
	"manjaniq/engine/rstaspoof"
	"sync"
	"unsafe"
)

var (
	mu     sync.Mutex
	engine *rstaspoof.Engine
)

//export Java_com_manjaniq_NativeBridge_GoHelloWorld
func Java_com_manjaniq_NativeBridge_GoHelloWorld(env *C.JNIEnv, clazz C.jclass) C.jstring {
	msg := C.CString("Manjaniq native bridge OK")
	defer C.free(unsafe.Pointer(msg))
	return C.NewStringUTF_wrapper(env, msg)
}

//export Java_com_manjaniq_NativeBridge_StartEngine
func Java_com_manjaniq_NativeBridge_StartEngine(env *C.JNIEnv, clazz C.jclass, configJSON C.jstring) C.jstring {
	configStr := jstringToString(env, configJSON)

	var cfg rstaspoof.Config
	if err := json.Unmarshal([]byte(configStr), &cfg); err != nil {
		return stringToJString(env, fmt.Sprintf("Config error: %v", err))
	}

	mu.Lock()
	defer mu.Unlock()
	if engine == nil {
		engine = rstaspoof.NewEngine()
	}

	if err := engine.Start(cfg); err != nil {
		return stringToJString(env, fmt.Sprintf("Start error: %v", err))
	}

	return stringToJString(env, "OK")
}

//export Java_com_manjaniq_NativeBridge_StopEngine
func Java_com_manjaniq_NativeBridge_StopEngine(env *C.JNIEnv, clazz C.jclass) C.jstring {
	mu.Lock()
	defer mu.Unlock()

	if engine == nil {
		return stringToJString(env, "OK")
	}

	if err := engine.Stop(); err != nil {
		return stringToJString(env, fmt.Sprintf("Stop error: %v", err))
	}

	return stringToJString(env, "OK")
}

//export Java_com_manjaniq_NativeBridge_IsEngineRunning
func Java_com_manjaniq_NativeBridge_IsEngineRunning(env *C.JNIEnv, clazz C.jclass) C.int {
	mu.Lock()
	defer mu.Unlock()

	if engine == nil {
		return 0
	}

	if engine.State() == rstaspoof.StateRunning {
		return 1
	}
	return 0
}

//export Java_com_manjaniq_NativeBridge_GetEngineState
func Java_com_manjaniq_NativeBridge_GetEngineState(env *C.JNIEnv, clazz C.jclass) C.jstring {
	mu.Lock()
	defer mu.Unlock()

	state := "STOPPED"
	if engine != nil {
		state = string(engine.State())
	}
	return stringToJString(env, state)
}

//export Java_com_manjaniq_NativeBridge_GetMetrics
func Java_com_manjaniq_NativeBridge_GetMetrics(env *C.JNIEnv, clazz C.jclass) C.jstring {
	mu.Lock()
	defer mu.Unlock()

	if engine == nil {
		return stringToJString(env, "{}")
	}

	metrics := engine.Metrics()
	data, err := json.Marshal(metrics)
	if err != nil {
		return stringToJString(env, "{}")
	}
	return stringToJString(env, string(data))
}

//export Java_com_manjaniq_NativeBridge_Cleanup
func Java_com_manjaniq_NativeBridge_Cleanup(env *C.JNIEnv, clazz C.jclass) {
	mu.Lock()
	defer mu.Unlock()

	if engine != nil {
		engine.Stop()
		engine = nil
	}
}

func jstringToString(env *C.JNIEnv, jstr C.jstring) string {
	// In cgo, C.jstring is a pointer type, but we need to check for nil
	// by using a helper C function that checks if it's NULL
	if C.jstringIsNil(env, jstr) != 0 {
		return ""
	}

	ptr := C.GetStringUTFChars_wrapper(env, jstr)
	if ptr == nil {
		return ""
	}
	defer C.ReleaseStringUTFChars_wrapper(env, jstr, ptr)

	return C.GoString(ptr)
}

func stringToJString(env *C.JNIEnv, str string) C.jstring {
	cStr := C.CString(str)
	defer C.free(unsafe.Pointer(cStr))
	return C.NewStringUTF_wrapper(env, cStr)
}

// main is required by Go for the package to be a valid main package.
func main() {}
