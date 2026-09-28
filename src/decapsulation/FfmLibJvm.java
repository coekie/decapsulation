package decapsulation;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/// Decapsulation by calling JNI API functions (from libjvm.so) via the Foreign Function & Memory API,
/// without loading any native library of our own.
class FfmLibJvm extends Decapsulater {
  public static void main(String[] args) {
    new FfmLibJvm().run();
  }

  @Override
  Lookup makeLookup() throws Throwable {
    Lookup lookup = MethodHandles.lookup();
    System.getProperties().put("decapsulation.lookup", lookup);
    Linker linker = Linker.nativeLinker();
    try (Arena arena = Arena.ofConfined()) {
      Path libjvmPath = System.getProperty("os.name").startsWith("Windows")
          ? Path.of(System.getProperty("java.home"), "bin", "server", "jvm.dll")
          : Path.of(System.getProperty("java.home"), "lib", "server", "libjvm.so");
      SymbolLookup jvmSymbols = SymbolLookup.libraryLookup(libjvmPath, arena);

      // Gets a reference to the JavaVM using `jint JNI_GetCreatedJavaVMs(JavaVM** vmBuf, jsize bufLen, jsize* nVMs)`
      // (of which there is normally only one).
      // Equivalent in native code:
      //   JavaVM *vm;
      //   JNI_GetCreatedJavaVMs(&vm, 1, NULL);
      MethodHandle getCreatedVMs = linker.downcallHandle(
          jvmSymbols.find("JNI_GetCreatedJavaVMs").orElseThrow(),
          FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
      MemorySegment vmBuf = arena.allocate(ADDRESS);
      getCreatedVMs.invoke(vmBuf, 1, MemorySegment.NULL);
      MemorySegment vm = vmBuf.get(ADDRESS, 0).reinterpret(Long.MAX_VALUE);

      // The `JNIInvokeInterface_*`, the JavaVM vtable. See `struct JNIInvokeInterface_` in jni.h:
      // [reserved*3, DestroyJavaVM, AttachCurrentThread, DetachCurrentThread, GetEnv, ...]
      // Equivalent in native code: (*vm)->...
      MemorySegment invokeInterface = vm.get(ADDRESS, 0).reinterpret(Long.MAX_VALUE);

      // Gets a reference to the JNIEnv, using `GetEnv(JavaVM*, JNIEnv**, jint version)`.
      // Equivalent in native code:
      //   JNIEnv *env;
      //   (*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_8);
      // GetEnv is at index 6 in the JNIInvokeInterface_ struct
      MethodHandle getEnv = linker.downcallHandle(
          invokeInterface.getAtIndex(ADDRESS, 6),
          FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
      MemorySegment envBuf = arena.allocate(ADDRESS);
      getEnv.invoke(vm, envBuf, 0x00010008 /* JNI_VERSION_1_8 */);
      MemorySegment env = envBuf.get(ADDRESS, 0).reinterpret(Long.MAX_VALUE);

      // the `JNINativeInterface_ *`. See `struct JNINativeInterface` in jni.h.
      // Equivalent in native code: (*env)->...
      MemorySegment nativeInterface = env.get(ADDRESS, 0).reinterpret(Long.MAX_VALUE);

      // Local references (jobjects) returned by JNI function calls go on the thread's current
      // JNIHandleBlock. That is cleared every time a regular Java native method returns
      // (e.g. Unsafe.allocateMemory0, called by arena.allocateFrom).
      // We use NewGlobalRef to turn them into global references, and free them with DeleteGlobalRef at the end.
      MethodHandle newGlobalRef = linker.downcallHandle(nativeInterface.getAtIndex(ADDRESS, 21),
          FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

      // Get the Lookup from System.getProperties().get("decapsulation.lookup").
      // Equivalent in native code:
      //   jclass systemClass = (*env)->FindClass(env, "java/lang/System");
      MethodHandle findClass = linker.downcallHandle(nativeInterface.getAtIndex(ADDRESS, 6),
          FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
      MemorySegment systemClass = (MemorySegment) newGlobalRef.invoke(env,
          (MemorySegment) findClass.invoke(env, arena.allocateFrom("java/lang/System")));
      // jmethodID getPropsMid = (*env)->GetStaticMethodID(env, systemClass,
      //     "getProperties", "()Ljava/util/Properties;");
      MethodHandle getStaticMethodID = linker.downcallHandle(nativeInterface.getAtIndex(ADDRESS, 113),
          FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
      MemorySegment getPropsMid = (MemorySegment) getStaticMethodID.invoke(env, systemClass,
          arena.allocateFrom("getProperties"),
          arena.allocateFrom("()Ljava/util/Properties;"));
      // jobject props = (*env)->CallStaticObjectMethod(env, systemClass, getPropsMid);
      MethodHandle callStaticObjectMethod = linker.downcallHandle(nativeInterface.getAtIndex(ADDRESS, 114),
          FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
      MemorySegment props = (MemorySegment) newGlobalRef.invoke(env,
          (MemorySegment) callStaticObjectMethod.invoke(env, systemClass, getPropsMid));
      // jmethodID getMid = (*env)->GetMethodID(env, (*env)->GetObjectClass(env, props),
      //     "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
      MethodHandle getObjectClass = linker.downcallHandle(nativeInterface.getAtIndex(ADDRESS, 31),
          FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
      MemorySegment propsClass = (MemorySegment) newGlobalRef.invoke(env,
          (MemorySegment) getObjectClass.invoke(env, props));
      MethodHandle getMethodID = linker.downcallHandle(nativeInterface.getAtIndex(ADDRESS, 33),
          FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
      MemorySegment getMid = (MemorySegment) getMethodID.invoke(env, propsClass,
          arena.allocateFrom("get"),
          arena.allocateFrom("(Ljava/lang/Object;)Ljava/lang/Object;"));
      // jstring keyStr = (*env)->NewStringUTF(env, "decapsulation.lookup");
      MethodHandle newStringUTF = linker.downcallHandle(nativeInterface.getAtIndex(ADDRESS, 167),
          FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
      MemorySegment keyStr = (MemorySegment) newGlobalRef.invoke(env,
          (MemorySegment) newStringUTF.invoke(env, arena.allocateFrom("decapsulation.lookup")));
      // jobject lookup = (*env)->CallObjectMethod(env, props, getMid, keyStr);
      MethodHandle callObjectMethod = linker.downcallHandle(nativeInterface.getAtIndex(ADDRESS, 34),
          FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
      MemorySegment lookupRef = (MemorySegment) newGlobalRef.invoke(env,
          (MemorySegment) callObjectMethod.invoke(env, props, getMid, keyStr));

      // Equivalent in plain Java: lookup.allowedModes = -1;
      // jclass lookupClass = (*env)->GetObjectClass(env, lookup);
      MemorySegment lookupClass = (MemorySegment) newGlobalRef.invoke(env,
          (MemorySegment) getObjectClass.invoke(env, lookupRef));
      // jfieldID allowedModesId = (*env)->GetFieldID(env, lookupClass, "allowedModes", "I");
      MethodHandle getFieldID = linker.downcallHandle(nativeInterface.getAtIndex(ADDRESS, 94),
          FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
      MemorySegment allowedModesId = (MemorySegment) getFieldID.invoke(env, lookupClass,
          arena.allocateFrom("allowedModes"),
          arena.allocateFrom("I"));
      // (*env)->SetIntField(env, lookup, allowedModesId, -1);
      MethodHandle setIntField = linker.downcallHandle(nativeInterface.getAtIndex(ADDRESS, 109),
          FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, JAVA_INT));
      setIntField.invoke(env, lookupRef, allowedModesId, -1);

      // Free our global references.
      // Equivalent in native code: (*env)->DeleteGlobalRef(env, ref);
      MethodHandle deleteGlobalRef = linker.downcallHandle(nativeInterface.getAtIndex(ADDRESS, 22),
          FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
      deleteGlobalRef.invoke(env, systemClass);
      deleteGlobalRef.invoke(env, props);
      deleteGlobalRef.invoke(env, propsClass);
      deleteGlobalRef.invoke(env, keyStr);
      deleteGlobalRef.invoke(env, lookupRef);
      deleteGlobalRef.invoke(env, lookupClass);
    }
    System.getProperties().remove("decapsulation.lookup");
    return lookup;
  }
}
