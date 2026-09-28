package decapsulation;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

/// Decapsulation by patching bytecode in-memory using Windows APIs.
class WinMemBytecode extends Decapsulater {
  public static void main(String[] args) {
    new WinMemBytecode().run();
  }

  static void setAllowedModes(DontLookup dontLookup, Lookup lookup) {
    // nonsense code that makes this method unique, easy to find in memory
    int x = 2;
    x = ((((x * x) ^ x) | x) + x) / x;
    // code to patch
    dontLookup.allowedModes = -1;
  }

  @Override
  Lookup makeLookup() throws Exception {
    new ProcessBuilder(
        "powershell", "-executionpolicy", "bypass", "-File", "bin/WinMemBytecode.ps1",
        "-processId", Long.toUnsignedString(ProcessHandle.current().pid()))
        .inheritIO() // to see output for debugging
        .start()
        .waitFor();

    Lookup lookup = MethodHandles.lookup();
    setAllowedModes(null, lookup);
    return lookup;
  }

  // a class that has the same fields (same in-memory layout) as Lookup, but mutable.
  static class DontLookup {
    Class<?> lookupClass;
    Class<?> prevLookupClass;
    volatile int allowedModes;
  }
}
