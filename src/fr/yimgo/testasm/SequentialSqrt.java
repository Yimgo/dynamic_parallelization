package fr.yimgo.testasm;

public class SequentialSqrt {
  public SequentialSqrt() { }
  public Double sqrt(Integer n) {
    Double sum = new Double(0);

    for (Integer i = 0; i < n; i += 1) {
      sum += Math.sqrt(i);
    }

    return sum;
  }
}
