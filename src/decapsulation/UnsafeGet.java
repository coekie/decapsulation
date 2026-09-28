package decapsulation;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;

/// Decapsulation using [Unsafe#getObject]
@SuppressWarnings("all")
class UnsafeGet extends Decapsulater {
  public static void main(String[] args) {
    new UnsafeGet().run();
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
    Field field = Lookup.class.getDeclaredField("IMPL_LOOKUP");
    Object staticFieldBase = unsafe.staticFieldBase(field);
    long offset = unsafe.staticFieldOffset(field);
    return (Lookup) unsafe.getObject(staticFieldBase, offset);
  }
}
