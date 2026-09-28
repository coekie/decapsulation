package decapsulation;

import io.github.toolfactory.narcissus.Narcissus;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;

/// Decapsulation by using the Narcissus library.
class UseNarcissus extends Decapsulater {
  public static void main(String[] args) {
    new UseNarcissus().run();
  }

  @Override
  Lookup makeLookup() throws Throwable {
    Lookup lookup = MethodHandles.lookup();
    Field allowedModes = Narcissus.findField(Lookup.class, "allowedModes");
    Narcissus.setField(lookup, allowedModes, -1);
    return lookup;
  }
}
