// native code used by decapsulation.UseJNI

#include <jni.h>

JNIEXPORT void JNICALL Java_decapsulation_UseJNI_setAllowedModes(JNIEnv *env, jclass cls, jobject lookup) {
    jclass lookupClass = (*env)->GetObjectClass(env, lookup);
    jfieldID allowedModesId = (*env)->GetFieldID(env, lookupClass, "allowedModes", "I");
    (*env)->SetIntField(env, lookup, allowedModesId, -1);
}
