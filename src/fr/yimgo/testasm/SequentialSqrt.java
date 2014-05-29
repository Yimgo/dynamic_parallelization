package fr.yimgo.testasm;

import org.pmw.tinylog.Logger;

public class SequentialSqrt {
  public SequentialSqrt() { }
  public Double sqrt(Integer n) {
    Double sum = new Double(0.0);

    for (Integer i = 0; i < n; i += 1) {
      Logger.trace(i + " " + sum);
      sum += (double) i;
    }

    return sum;
  }
}
