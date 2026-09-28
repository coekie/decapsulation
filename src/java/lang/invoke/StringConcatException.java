package java.lang.invoke;

import java.lang.invoke.MethodHandles.Lookup;

public class StringConcatException extends Exception {
  public static Lookup L = Lookup.IMPL_LOOKUP;

  static int padding;

  // original implementation

  private static final long serialVersionUID = 301L;

  public StringConcatException(String msg) {
    super(msg);
  }

  public StringConcatException(String msg, Throwable cause) {
    super(msg, cause);
  }
}
