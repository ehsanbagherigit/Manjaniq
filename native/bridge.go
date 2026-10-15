package main

/*
#include <jni.h>
#include <stdlib.h>

jstring NewStringUTF_wrapper(JNIEnv *env, const char *str);
*/
import "C"
import "unsafe"

//export Java_com_manjaniq_NativeBridge_GoHelloWorld
func Java_com_manjaniq_NativeBridge_GoHelloWorld(env *C.JNIEnv, clazz C.jclass) C.jstring {
	msg := C.CString("Manjaniq native bridge OK")
	defer C.free(unsafe.Pointer(msg))
	return C.NewStringUTF_wrapper(env, msg)
}

func main() {}
