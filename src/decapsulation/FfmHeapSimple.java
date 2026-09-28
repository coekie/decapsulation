package decapsulation;

import jdk.jfr.consumer.RecordingStream;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.concurrent.CompletableFuture;

import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.util.concurrent.TimeUnit.SECONDS;

/// Simplified version of [FfmHeap].
/// Exists to help explain how [FfmHeap] works without getting distracted by extra complications:
/// * Assumes OOPs are 64 bits, does not deal with compressed (32 bit) OOPs.
///   So this must run with `-XX:-UseCompressedOops` or with a large heap, which prevents the JVM from compressing OOPs.
/// * Assumes no false positives when looking for our marker values in memory.
public class FfmHeapSimple extends Decapsulater {
  private final Victim victim = new Victim();
  
  public static void main(String[] args) {
    new FfmHeapSimple().run();
  }

  @Override
  Lookup makeLookup() throws Throwable {
    MemorySegment victimSegment = findVictim(findHeap());

    // assign victim.lookup to victim.dontLookup
    long lookupOop = victimSegment.get(JAVA_LONG, 0);
    victimSegment.set(JAVA_LONG, 8, lookupOop);

    if (victim.dontLookup.getClass() != (Class<?>) Lookup.class) {
      throw new RuntimeException("Manipulating Lookup reference failed");
    }

    // with type confusion, this is _actually_ writing to lookup.allowedModes.
    victim.dontLookup.allowedModes = -1; // -1 is Lookup.TRUSTED

    return victim.lookup;
  }

  /// Find the location of our [Victim] instance in memory.
  /// Returns a [MemorySegment] pointing at the [Victim#lookup] field.
  private MemorySegment findVictim(MemorySegment heap) {
    for (long i = 0; i < heap.byteSize() - 32; i += 8) {
      if (heap.get(JAVA_LONG, i) == victim.a
          && heap.get(JAVA_LONG, i + 8) == victim.b) {
        return heap.asSlice(i + 16);
      }
    }
    throw new RuntimeException("Failed to find victim in memory");
  }

  /// Capture a `jdk.GCHeapSummary` jfr event to find the address of the heap
  private static MemorySegment findHeap() throws Exception {
    CompletableFuture<MemorySegment> future = new CompletableFuture<>();
    try (RecordingStream rs = new RecordingStream()) {
      rs.enable("jdk.GCHeapSummary");
      rs.onEvent("jdk.GCHeapSummary", e -> {
        if (e.getString("when").equals("After GC")) {
          long start = e.getLong("heapSpace.start");
          long end = e.getLong("heapSpace.committedEnd");
          future.complete(MemorySegment.ofAddress(start).reinterpret(end - start));
        }
      });
      rs.startAsync();
      System.gc();
      return future.get(5, SECONDS);
    }
  }

  static class Victim {
    // Semi-random values that will help us find this object in memory
    long a = 0xF108F703F405F602L;
    long b = 0xF207F170E3457833L;

    // the lookup that we will be modifying
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
