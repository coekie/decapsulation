package decapsulation;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.file.Files;
import java.nio.file.Path;

/// Simplified version of [ProcMemHeap].
/// Exists to help explain how [ProcMemHeap] works without getting distracted by extra complications:
/// * No buffer when reading; reading one long at a time. This makes it slow.
/// * Assumes OOPs are 64 bits, does not deal with compressed (32 bit) OOPs.
///   So this must run with `-XX:-UseCompressedOops` or with a large heap, which prevents the JVM from compressing OOPs.
/// * Assumes no false positives when looking for our marker values in memory.
class ProcMemHeapSimple extends Decapsulater {
  private final Victim victim = new Victim();

  public static void main(String[] args) {
    new ProcMemHeapSimple().run();
  }

  @Override
  Lookup makeLookup() throws IOException {
    AddressRange heap = findHeap();
    try (RandomAccessFile procMem = new RandomAccessFile("/proc/self/mem", "rw")) {
      long pos = findVictim(heap, procMem);

      procMem.seek(pos);
      long lookupOop = procMem.readLong(); // read `Victim.lookup`
      procMem.writeLong(lookupOop); // write to the next position, that is to `Victim.dontLookup`

      if (victim.dontLookup.getClass() != (Class<?>) Lookup.class) {
        throw new RuntimeException("Manipulating Lookup reference failed");
      }

      // with type confusion, this is actually writing to victim.lookup.allowedModes.
      victim.dontLookup.allowedModes = -1; // -1 is Lookup.TRUSTED

      return victim.lookup;
    }
  }

  /// Capture a `jdk.GCHeapSummary` jfr event to find the address of the heap.
  /// This is a slightly different version than in e.g. [FfmHeap] because
  /// we want this to work on JDK 11, which does not support `RecordingStream`.
  private static AddressRange findHeap() throws IOException {
    Path tmp = Files.createTempFile("heap", ".jfr");
    try {
      try (Recording recording = new Recording()) {
        recording.enable("jdk.GCHeapSummary");
        recording.start();
        System.gc();
        recording.stop();
        recording.dump(tmp);
      }
      try (RecordingFile file = new RecordingFile(tmp)) {
        while (file.hasMoreEvents()) {
          RecordedEvent e = file.readEvent();
          if (e.getEventType().getName().equals("jdk.GCHeapSummary")) {
            long start = e.getLong("heapSpace.start");
            long end = e.getLong("heapSpace.committedEnd");
            return new AddressRange(start, end);
          }
        }
      }
      throw new RuntimeException("No jdk.GCHeapSummary recorded");
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  /// Find our [Victim] instance in memory. Returns the location of the [Victim#lookup] field.
  private long findVictim(AddressRange heap, RandomAccessFile procMem) throws IOException {
    long pos = heap.start;
    procMem.seek(pos);

    while (pos < heap.end - 16) {
      long read = Long.reverseBytes(procMem.readLong());
      pos += 8;

      if (read == victim.a) {
        read = Long.reverseBytes(procMem.readLong());
        pos += 8;
        if (read == victim.b) {
          return pos;
        }
      }
    }

    throw new RuntimeException("Failed to find victim in memory");
  }

  /// A memory region, with start and end address
  static final class AddressRange {
    private final long start;
    private final long end;

    AddressRange(long start, long end) {
      this.start = start;
      this.end = end;
    }
  }

  static class Victim {
    // Semi-random values that will help us find this object in memory
    long a = 0xF108F703F405F602L;
    long b = 0xF207F170E3457833L;

    // the lookup that we will modify
    Lookup lookup = MethodHandles.lookup();

    // the field that we (incorrectly) will update to contain `lookup`
    volatile DontLookup dontLookup;
  }

  // a class that has the same fields (same in-memory layout) as Lookup, but mutable.
  static class DontLookup {
    Class<?> lookupClass;
    Class<?> prevLookupClass;
    volatile int allowedModes;
  }
}
