package decapsulation;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import sun.misc.Unsafe;

/// Decapsulation using [Unsafe#putInt].
///
/// A bit more complicated than [UnsafeGet], but shows that reflection hiding
/// the field doesn't stop this.
@SuppressWarnings("all")
class UnsafePut extends Decapsulater {
  public static void main(String[] args) {
    new UnsafePut().run();
  }

  // the "usual" way of getting Unsafe
  static Unsafe getUnsafe() throws ReflectiveOperationException {
    Field f = Unsafe.class.getDeclaredField("theUnsafe");
    f.setAccessible(true);
    return (Unsafe) f.get(null);
  }

  @Override
  Lookup makeLookup() throws ReflectiveOperationException {
    Unsafe unsafe = getUnsafe();

    // This does the equivalent of:
    // `unsafe.objectFieldOffset(Lookup.class.getDeclaredField("allowedModes"))`,
    // which doesn't work with Lookup.class because that field is filtered by reflection
    long offset = unsafe.objectFieldOffset(DontLookup.class.getDeclaredField("allowedModes"));

    Lookup lookup = MethodHandles.lookup();
    unsafe.putInt(lookup, offset, -1); // -1 is Lookup.TRUSTED
    return lookup;
  }

  // a class that has the same fields (same in-memory layout) as Lookup
  static class DontLookup {
    Class<?> lookupClass;
    Class<?> prevLookupClass;
    int allowedModes;
  }
}
