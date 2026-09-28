package decapsulation;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;

/// Decapsulation by reflection, using `setAccessible` on the now-hidden allowedModes field.
/// Only works in old Java versions.
class SetAccessibleFiltered extends Decapsulater {
  public static void main(String[] args) {
    new SetAccessibleFiltered().run();
  }

  @Override
  Lookup makeLookup() throws Throwable {
    try {
      Lookup lookup = MethodHandles.lookup();
      Field allowedModes = Lookup.class.getDeclaredField("allowedModes");
      allowedModes.setAccessible(true);
      allowedModes.set(lookup, -1); // -1 is Lookup.TRUSTED
      return lookup;
    } catch (NoSuchFieldException e) { // newer java versions hide the "allowedModes" field
      throw expectedFailure(e);
    }
  }
}
