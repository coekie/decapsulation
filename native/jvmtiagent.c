// native code used by decapsulation.AttachJvmtiAgent

#include <jni.h>
#include <jvmti.h>

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *vm, char *options, void *reserved) {
    JNIEnv *env;
    (*vm)->AttachCurrentThread(vm, (void **)&env, NULL);

    jclass ourClass = (*env)->FindClass(env, "decapsulation/AttachJvmtiAgent");
    jfieldID lookupField = (*env)->GetStaticFieldID(env, ourClass, "LOOKUP",
        "Ljava/lang/invoke/MethodHandles$Lookup;");
    jobject lookup = (*env)->GetStaticObjectField(env, ourClass, lookupField);

    jclass lookupClass = (*env)->GetObjectClass(env, lookup);
    jfieldID allowedModesId = (*env)->GetFieldID(env, lookupClass, "allowedModes", "I");
    (*env)->SetIntField(env, lookup, allowedModesId, -1);

    return JNI_OK;
}
