#include <jni.h>
#include <stdlib.h>

jstring NewStringUTF_wrapper(JNIEnv *env, const char *str) {
    return (*env)->NewStringUTF(env, str);
}
