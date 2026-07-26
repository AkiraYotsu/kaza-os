#include <jni.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

JNIEXPORT jstring JNICALL
Java_com_kazaos_trkz_TrKZActivity_stringFromJNI(JNIEnv* env, jobject obj) {
    return (*env)->NewStringUTF(env, "Kaza OS v1.1 Native Engine Linked Successfully");
}

JNIEXPORT jint JNICALL
Java_com_kazaos_trkz_TrKZActivity_executeKazaCommand(JNIEnv* env, jobject obj, jstring cmd) {
    const char *native_cmd = (*env)->GetStringUTFChars(env, cmd, 0);
    // Execute native command logic
    (*env)->ReleaseStringUTFChars(env, cmd, native_cmd);
    return 0;
}
