package decapsulation;

import com.sun.management.HotSpotDiagnosticMXBean;
import jdk.jfr.consumer.RecordingStream;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CompletableFuture;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.util.concurrent.TimeUnit.SECONDS;

/// Decapsulation by updating objects in the heap using FFM.
class FfmHeap extends Decapsulater {
  private final Victim victim = new Victim();
  
  public static void main(String[] args) {
    new FfmHeap().run();
  }

  @Override
  Lookup makeLookup() throws Throwable {
    MemorySegment victimSegment = findVictim(findHeap());

    // `lookup` is expected to be at the start of victimSegment, or a little further down (because of padding and
    // alignment that can differ based on JDK version).
    if (useCompressedOops()) { // 32-bit OOPs
      int offset = victimSegment.get(JAVA_INT, 0) == 0 ? 4 : 0;
      int lookupOop = victimSegment.get(JAVA_INT, offset);
      victimSegment.set(JAVA_INT, offset + 4, lookupOop);
    } else { // 64-bit OOPs
      int offset = victimSegment.get(JAVA_LONG, 0) == 0 ? 8 : 0;
      long lookupOop = victimSegment.get(JAVA_LONG, offset);
      victimSegment.set(JAVA_LONG, offset + 8, lookupOop);
    }

    if (victim.dontLookup.getClass() != (Class<?>) Lookup.class) {
      throw new RuntimeException("Manipulating Lookup reference failed");
    }

    // with type confusion, this is _actually_ writing to lookup.allowedModes.
    victim.dontLookup.allowedModes = -1; // -1 is Lookup.TRUSTED

    return victim.lookup;
  }

  private static boolean useCompressedOops() {
    return ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
        .getVMOption("UseCompressedOops").getValue().equals("true");
  }

  /// Find the location of our [Victim] instance in memory
  private MemorySegment findVictim(MemorySegment heap) {
    for (long i = 0; i < heap.byteSize() - 32; i += 8) {
      if (heap.get(JAVA_LONG, i) == victim.a
          && heap.get(JAVA_LONG, i + 8) == victim.b) {
        // modify the value and read it again, to be sure we found the right
        // value and not another copy of it (e.g. a garbage-collected version)
        long originalB = victim.b;
        victim.b = 0x5555555555555555L;
        VarHandle.fullFence(); // avoid that optimizations reorder the write and the read of victim.b
        long again = heap.get(JAVA_LONG, i + 8);

        if (again == 0x5555555555555555L) { // it matches our updated value, we found Victim
          return heap.asSlice(i + 16);
        } else {
          // False positive, continue searching
          victim.b = originalB;
        }
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
    // dummy field to take up some space, to avoid that "lookup" field could get squeezed in an alignment gap before "a"
    int padding;

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
