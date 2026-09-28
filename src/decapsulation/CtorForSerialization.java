package decapsulation;

import sun.reflect.ReflectionFactory;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;

/// Decapsulation using [ReflectionFactory#newConstructorForSerialization].
class CtorForSerialization extends Decapsulater {

  public static void main(String[] args) throws Exception {
    new CtorForSerialization().run();
  }

  @Override
  Lookup makeLookup() throws ReflectiveOperationException {
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
}
