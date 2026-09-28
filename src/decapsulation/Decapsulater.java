package decapsulation;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;

/// Base class for all decapsulation tests
abstract class Decapsulater {
  abstract Lookup makeLookup() throws Throwable;

  void run() {
    try {
      String foo = new String("foo");
      Lookup lookup = makeLookup();
      MethodHandle getValue = lookup.findGetter(String.class, "value", byte[].class);
      byte[] value = (byte[]) getValue.invokeExact(foo);
      value[0] = 'm';
      if (foo.equals("moo")) {
        pass();
      } else {
        throw new AssertionError();
      }
    } catch (ExpectedFailureException ignored) {
    } catch (Throwable t) {
      System.out.println("ERROR");
      throw new RuntimeException(t);
    }
  }

  private void pass() {
    System.out.println("PASS");
  }

  @SuppressWarnings("CallToPrintStackTrace")
  RuntimeException expectedFailure(Throwable t) {
    t.printStackTrace();
    System.out.println("FAIL");
    throw new ExpectedFailureException(t);
  }

  private static class ExpectedFailureException extends RuntimeException {
    private ExpectedFailureException(Throwable cause) {
      super(cause);
    }
  }
}
