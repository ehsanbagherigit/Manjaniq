#include <jni.h>
#include <stdlib.h>
#include <string.h>

// Wrapper for creating a UTF string
jstring NewStringUTF_wrapper(JNIEnv* env, const char* str) {
    return (*env)->NewStringUTF(env, str);
}

// Wrapper for getting UTF chars from a jstring
const char* GetStringUTFChars_wrapper(JNIEnv* env, jstring str) {
    return (*env)->GetStringUTFChars(env, str, NULL);
}

// Wrapper for releasing UTF chars
void ReleaseStringUTFChars_wrapper(JNIEnv* env, jstring str, const char* chars) {
    (*env)->ReleaseStringUTFChars(env, str, chars);
}

// Helper to check if a jstring is NULL
int jstringIsNil(JNIEnv* env, jstring str) {
    return (str == NULL) ? 1 : 0;
}
