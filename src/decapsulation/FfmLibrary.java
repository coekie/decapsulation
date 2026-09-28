package decapsulation;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.file.Path;

/// Decapsulation by loading a custom native library via FFM.
/// Calls a "setAllowedModes" function in it that sets allowedModes on the lookup to -1.
class FfmLibrary extends Decapsulater {
  public static void main(String[] args) {
    new FfmLibrary().run();
  }

  @Override
  Lookup makeLookup() throws Throwable {
    Lookup lookup = MethodHandles.lookup();
    System.getProperties().put("decapsulation.lookup", lookup);
    Path lib = Path.of(System.getProperty("os.name").startsWith("Windows")
        ? "native/ffmlibrary.dll" : "native/ffmlibrary.so");
    try (Arena arena = Arena.ofConfined()) {
      SymbolLookup symbols = SymbolLookup.libraryLookup(lib, arena);
      MethodHandle setAllowedModes = Linker.nativeLinker().downcallHandle(
          symbols.find("setAllowedModes").orElseThrow(),
          FunctionDescriptor.ofVoid());
      setAllowedModes.invoke();
    }
    System.getProperties().remove("decapsulation.lookup");
    return lookup;
  }
}
