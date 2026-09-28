package decapsulation;

import com.sun.management.HotSpotDiagnosticMXBean;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;

/// Decapsulation by updating objects in the heap using `/proc/self/mem`.
class ProcMemHeap extends Decapsulater {
  private final Victim victim = new Victim();

  public static void main(String[] args) {
    new ProcMemHeap().run();
  }

  @Override
  Lookup makeLookup() throws IOException {
    AddressRange heap = findHeap();
    try (RandomAccessFile procMem = new RandomAccessFile("/proc/self/mem", "rw")) {
      long pos = findVictim(heap, procMem);

      // `lookup` is expected to be at `pos`, or a little further down (because of padding and alignment that can differ
      // based on JDK version).
      procMem.seek(pos);
      if (useCompressedOops()) { // 32-bit OOPs
        int lookup = procMem.readInt();
        if (lookup == 0) { // padding before lookup, look further
          lookup = procMem.readInt();
        }
        procMem.writeInt(lookup);
      } else { // 64-bit OOPs
        long lookup = procMem.readLong();
        if (lookup == 0) { // padding before lookup, look further
          lookup = procMem.readLong();
        }
        procMem.writeLong(lookup);
      }

      if (victim.dontLookup.getClass() != (Class<?>) Lookup.class) {
        throw new RuntimeException("Manipulating Lookup reference failed");
      }

      // with type confusion, this is actually writing to victim.lookup.allowedModes.
      victim.dontLookup.allowedModes = -1; // -1 is Lookup.TRUSTED

      return victim.lookup;
    }
  }

  private static boolean useCompressedOops() {
    return ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
        .getVMOption("UseCompressedOops").getValue().equals("true");
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

  /// The bytes that we search for to find our [Victim] instance in memory
  private byte[] searchPattern() throws IOException {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    DataOutputStream dout = new DataOutputStream(bout);
    dout.writeLong(Long.reverseBytes(victim.a)); // reverse for little-endian byte order
    dout.writeLong(Long.reverseBytes(victim.b));
    return bout.toByteArray();
  }

  /// Find our [Victim] instance in memory. Returns the location right after the [Victim#b] field.
  private long findVictim(AddressRange heap, RandomAccessFile procMem) throws IOException {
    byte[] search = searchPattern();
    byte[] buf = new byte[4096];

    long pos = heap.start;
    procMem.seek(pos);
    int searchPos = 0;

    while (pos < heap.end) {
      int readLen = (int) Math.min(buf.length, heap.end - pos);
      procMem.readFully(buf, 0, readLen);

      for (int i = 0; i < readLen; i++) {
        byte b = buf[i];
        if (b == search[searchPos]) {
          searchPos++;
          if (searchPos == search.length) {
            // modify the value and read it again, to be sure we found the right
            // value and not another copy of it (e.g. garbage-collected version or our `search` array)
            long originalB = victim.b;
            victim.b = 0x5555555555555555L;
            procMem.seek(pos);
            procMem.readFully(buf, 0, readLen);

            if (buf[i] == 0x55) { // it matches our updated value, we found Victim
              return pos + i + 1;
            } else {
              // False positive, continue searching
              victim.b = originalB;
              searchPos = 0;
            }
          }
        } else {
          searchPos = 0;
        }
      }
      pos += readLen;
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
    // dummy field to take up some space to avoid that "lookup" field could get squeezed in an alignment gap before "a"
    int padding = 0;

    // Semi-random values (without repeated bytes) that will help us find this object in memory
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
