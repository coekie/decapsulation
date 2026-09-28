package decapsulation;

import java.io.*;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.channels.Channels;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.Objects.requireNonNull;

/// Decapsulation by patching a class in the JVM's jimage (lib/modules) file.
///
/// Requires write access to $JAVA_HOME/lib/modules.
class PatchJimage extends Decapsulater {
  public static void main(String[] args) {
    new PatchJimage().run();
  }

  @Override
  Lookup makeLookup() throws Exception {
    byte[] original = getResourceAsBytes("java/lang/invoke/StringConcatException.class");
    byte[] patched = generatePatchedClass(original.length);

    File modulesFile = new File(System.getProperty("java.home"), "lib/modules");
    try (RandomAccessFile raf = new RandomAccessFile(modulesFile, "rw")) {
      long offset = findOffsetInFile(raf, original);

      raf.seek(offset);
      raf.write(patched);

      try {
        return (Lookup) Class.forName("java.lang.invoke.StringConcatException").getDeclaredField("L").get(null);
      } finally {
        // Restore original code so the JDK is not permanently modified
        raf.seek(offset);
        raf.write(original);
      }
    }
  }

  // Search for `search` in the `file`.
  // Naive/fast search algorithm, works because first byte of `search` only occurs once.
  private static long findOffsetInFile(RandomAccessFile file, byte[] search) throws Exception {
    BufferedInputStream in = new BufferedInputStream(Channels.newInputStream(file.getChannel()));
    long pos = 0;
    int searchPos = 0;
    int b;
    while ((b = in.read()) != -1) {
      pos++;
      if ((byte) b == search[searchPos]) {
        searchPos++;
        if (searchPos == search.length) {
          return pos - search.length;
        }
      } else {
        searchPos = (byte) b == search[0] ? 1 : 0;
      }
    }
    throw new RuntimeException("Class not found in jimage");
  }

  /// Generate a replacement StringConcatException class, that exposes Lookup.IMPL_LOOKUP.
  /// It has the same size as the JDK StringConcatException, so that it fits exactly in the
  /// same slot in the jimage file.
  /// That is accomplished by extending the {@code padding} field name.
  private byte[] generatePatchedClass(int targetSize) throws Exception {
    byte[] base = getResourceAsBytes("decapsulation/StringConcatException_patch.class");
    if (base.length > targetSize)
      throw new RuntimeException("Patched class is larger than slot size");
    int delta = targetSize - base.length;

    // The constant for "padding" looks like: tag(0x01) + length(0x00, 0x07) + "padding"
    String original = "\1\0\7padding";
    int newLength = "padding".length() + delta;
    String replacement = "\1" + (char)(newLength >> 8) + (char)(newLength & 0xFF)
        + "padding" + "a".repeat(delta);
    // Abusing ISO_8859_1 (which has a one-to-one mapping between chars and bytes)
    // is the easiest way to do a search & replace in a byte array
    return new String(base, ISO_8859_1)
        .replace(original, replacement)
        .getBytes(ISO_8859_1);
  }

  private static byte[] getResourceAsBytes(String name) throws IOException {
    try (InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream(name)) {
      return requireNonNull(in).readAllBytes();
    }
  }
}
