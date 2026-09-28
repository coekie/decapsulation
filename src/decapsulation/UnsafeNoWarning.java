package decapsulation;

import sun.misc.Unsafe;
import sun.reflect.ReflectionFactory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/// Demonstration that by using [CtorForSerialization]'s trick, you can also use [Unsafe] without triggering warnings.
///
/// For demonatration purposes only.
/// This stunt is performed by a professional programmer on a closed course - do not try this at home.
@SuppressWarnings("all")
class UnsafeNoWarning extends Decapsulater {
  public static void main(String[] args) {
    new UnsafeNoWarning().run();
  }

  static Unsafe getUnsafe() throws Throwable {
    disableUnsafeWarning();
    Field f = Unsafe.class.getDeclaredField("theUnsafe");
    f.setAccessible(true);
    return (Unsafe) f.get(null);
  }

  static void disableUnsafeWarning() throws Throwable {
    Lookup lookup = makeLookupWithCtorForSerialization();
    try {
      lookup.findStatic(Unsafe.class, "trySetMemoryAccessWarned", MethodType.methodType(boolean.class))
          .invoke();
    } catch (NoSuchMethodException e) {
      // ignore. if there is no `trySetMemoryAccessWarned` then there presumably is no warning
    }
  }

  static Lookup makeLookupWithCtorForSerialization() throws ReflectiveOperationException {
    ReflectionFactory reflectionFactory = ReflectionFactory.getReflectionFactory();
    try {
      Constructor<?> ctor = reflectionFactory.newConstructorForSerialization(Lookup.class,
          Lookup.class.getDeclaredConstructor(Class.class, Class.class, int.class));
      return (Lookup) ctor.newInstance(Object.class, null, -1);
    } catch (NoSuchMethodException e) {
      Constructor<?> ctor = reflectionFactory.newConstructorForSerialization(Lookup.class,
          Lookup.class.getDeclaredConstructor(Class.class, int.class));
      return (Lookup) ctor.newInstance(Object.class, -1);
    }
  }

  // ----- You can stop reading here ------

  /// Here we use the [Unsafe] abtained above to get another [Lookup]. That's kinda silly because we already
  /// had a trusted [Lookup] to start with. That is just to fit into the existing test infrastructure in this repo.
  /// The point is showing that if you would have existing code using [Unsafe] to do other things, then this would
  /// bypass the warning.

  @Override
  Lookup makeLookup() throws Throwable {
    Unsafe unsafe = getUnsafe();
    Field field = Lookup.class.getDeclaredField("IMPL_LOOKUP");
    Object staticFieldBase = unsafe.staticFieldBase(field);
    long offset = unsafe.staticFieldOffset(field);
    return (Lookup) unsafe.getObject(staticFieldBase, offset);
  }
}
