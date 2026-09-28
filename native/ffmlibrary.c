// native code used by decapsulation.FfmLibrary

#include <jni.h>

// Sets allowedModes = -1 on the Lookup passed via the "decapsulation.lookup" system property.
// Called from Java via FFM (SymbolLookup.libraryLookup + downcallHandle).
void setAllowedModes() {
    JavaVM *vm;
    JNI_GetCreatedJavaVMs(&vm, 1, NULL);
    JNIEnv *env;
    (*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8);

    // Get the Lookup from `System.getProperties().get("decapsulation.lookup")`.
    // An alternative would have been using a static field in our java class,
    // but `(*env)->FindClass(...)` would be looking for the class in the wrong classloader: the last Java frame
    // seen by HotSpot is inside java.lang.invoke internals, so it uses the bootstrap class loader.
    jclass systemClass = (*env)->FindClass(env, "java/lang/System");
    jmethodID getPropsMid = (*env)->GetStaticMethodID(env, systemClass,
        "getProperties", "()Ljava/util/Properties;");
    jobject props = (*env)->CallStaticObjectMethod(env, systemClass, getPropsMid);
    jclass propsClass = (*env)->GetObjectClass(env, props);
    jmethodID getMid = (*env)->GetMethodID(env, propsClass,
        "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
    jstring keyStr = (*env)->NewStringUTF(env, "decapsulation.lookup");
    jobject lookup = (*env)->CallObjectMethod(env, props, getMid, keyStr);

    jclass lookupClass = (*env)->GetObjectClass(env, lookup);
    jfieldID allowedModesId = (*env)->GetFieldID(env, lookupClass, "allowedModes", "I");
    (*env)->SetIntField(env, lookup, allowedModesId, -1);
}
