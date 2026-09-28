package decapsulation;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

/// Decapsulation by patching bytecode in-memory using FFM.
class FfmBytecode extends Decapsulater {
  public static void main(String[] args) {
    new FfmBytecode().run();
  }

  static void setAllowedModes(DontLookup dontLookup, Lookup lookup) {
    // nonsense code that makes this method unique and easy to find in memory
    int x = 2;
    x = ((((x * x) ^ x) | x) + x) / x;
    // code to patch
    dontLookup.allowedModes = -1;
  }

  @Override
  Lookup makeLookup() throws IOException {
    patchSetAllowedModes();
    Lookup lookup = MethodHandles.lookup();
    setAllowedModes(null, lookup);
    return lookup;
  }

  /// Patch `setAllowedModes` to update `lookup` instead of `dontLookup`
  private static void patchSetAllowedModes() throws IOException {
    // Implementation of setAllowedModes(), in bytecode format
    byte[] search = {
        0x05, 0x3d, // x=2 (iconst_2, istore_2)
        0x1c, 0x1c, 0x68, // x*x (iload_2, iload_2, imul)
        0x1c, (byte) 0x82, // ^x (iload_2, ixor)
        0x1c, (byte) 0x80, // |x (iload_2, ior)
        0x1c, 0x60, // +x (iload_2, iadd)
        0x1c, 0x6c, // /x (iload_2, idiv)
        0x3d, // x=... (istore_2)
        0x2a, // dontLookup (aload_0)
        // in the real `setAllowedModes` implementation the next byte is 0x02 (iconst_m1).
        // we deliberately put a different dummy value here to distinguish between
        // finding `setAllowedModes`, and finding this byte[] itself.
        0x00
    };
    MemorySegment searchSegment = MemorySegment.ofArray(search);

    for (MemorySegment region : findRegions()) {
      for (long i = 0; i <= region.byteSize() - search.length; i++) {
        // if all bytes match except the last one, then we found `setAllowedModes`
        if (region.asSlice(i, search.length).mismatch(searchSegment) == search.length - 1) {
          // change the accessed value from dontLookup (0x2a = aload_0, loading the first parameter),
          // to lookup (0x2b = aload_1, loading the second parameter)
          region.set(JAVA_BYTE, i + search.length - 2, (byte) 0x2b);
        }
      }
    }
  }

  private static List<MemorySegment> findRegions() throws IOException {
    try (var lines = Files.lines(Path.of("/proc/self/maps"))) {
      return lines.map(line -> line.split(" +"))
          // example of a line we want to match and parse:
          // 707600000-717000000 rw-p 00000000 00:00 0
          .filter(line -> line.length == 5 && line[1].startsWith("rw-") && line[4].equals("0"))
          .map(split -> {
            String[] startAndEnd = split[0].split("-");
            long start = Long.parseUnsignedLong(startAndEnd[0], 16);
            long end = Long.parseUnsignedLong(startAndEnd[1], 16);
            return MemorySegment.ofAddress(start).reinterpret(end - start);
          })
          // optimization: the code we're looking for lives in a small metaspace chunk
          .filter(s -> s.byteSize() < 32_000_000)
          .toList();
    }
  }

  // a class that has the same fields (same in-memory layout) as Lookup, but mutable.
  static class DontLookup {
    Class<?> lookupClass;
    Class<?> prevLookupClass;
    volatile int allowedModes;
  }
}
