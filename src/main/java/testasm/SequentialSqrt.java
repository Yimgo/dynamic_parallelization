package testasm;

import org.pmw.tinylog.Logger;

public class SequentialSqrt {
  public SequentialSqrt() { }
  public Double sqrt(Integer n) {
    for (Integer i = 0; i < n; i += 1) {
      System.out.println(i);
    }

    return new Double(n * (n + 1) / 2);
  }
}
