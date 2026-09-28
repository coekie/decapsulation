package decapsulation;

import com.sun.management.HotSpotDiagnosticMXBean;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;

/// Decapsulation by updating objects in the heap using Windows APIs.
class WinMemHeap extends Decapsulater {
  private final Victim victim = new Victim();

  public static void main(String[] args) {
    new WinMemHeap().run();
  }

  @Override
  Lookup makeLookup() throws Exception {
    AddressRange heap = findHeap();
    List<String> cmd = new ArrayList<>(List.of(
        "powershell", "-executionpolicy", "bypass", "-File", "bin/WinMemHeap.ps1",
        "-processId", Long.toUnsignedString(ProcessHandle.current().pid()),
        "-heapStart", Long.toUnsignedString(heap.start),
        "-heapEnd", Long.toUnsignedString(heap.end)));
    if (useCompressedOops()) cmd.add("-useCompressedOops");
    new ProcessBuilder(cmd)
        .inheritIO() // to see output for debugging
        .start()
        .waitFor();

    if (victim.dontLookup.getClass() != (Class<?>) Lookup.class) {
      throw new RuntimeException("Manipulating Lookup reference failed");
    }

    // update dontLookup.allowedModes, which is _actually_ writing to lookup.allowedModes.
    victim.dontLookup.allowedModes = -1; // -1 is Lookup.TRUSTED
    return victim.lookup;
  }

  private static boolean useCompressedOops() {
    return ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
        .getVMOption("UseCompressedOops").getValue().equals("true");
  }

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

  static final class AddressRange {
    final long start;
    final long end;

    AddressRange(long start, long end) {
      this.start = start;
      this.end = end;
    }
  }

  static class Victim {
    // dummy field to take up some space, to avoid that "lookup" field could get squeezed in an alignment gap before "a"
    int padding = 0;
    // A couple values that will help us find this object in memory
    long a = 0xF108F703F405F602L; // semi-random value (without repeated bytes)
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
