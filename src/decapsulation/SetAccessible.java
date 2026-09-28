package decapsulation;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;

/// Decapsulation by reflection, using `setAccessible`. Only works in old Java versions.
class SetAccessible extends Decapsulater {
  public static void main(String[] args) {
    new SetAccessible().run();
  }

  @Override
  Lookup makeLookup() throws Throwable {
    try {
      Field implLookup = Lookup.class.getDeclaredField("IMPL_LOOKUP");
      implLookup.setAccessible(true);
      return (Lookup) implLookup.get(null);
    } catch (InaccessibleObjectException e) {
      throw expectedFailure(e);
    }
  }
}
