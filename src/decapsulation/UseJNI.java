package decapsulation;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

/// Decapsulation by loading a native library through JNI.
class UseJNI extends Decapsulater {
  static {
    String libFile = System.getProperty("os.name").startsWith("Windows")
        ? "native/usejni.dll" : "native/usejni.so";
    System.load(new File(libFile).getAbsolutePath());
  }

  private static native void setAllowedModes(Object lookup);

  public static void main(String[] args) {
    new UseJNI().run();
  }

  @Override
  Lookup makeLookup() {
    Lookup lookup = MethodHandles.lookup();
    setAllowedModes(lookup);
    return lookup;
  }
}
