package fr.yimgo.testasm;

public class SequentialSqrt {
  public SequentialSqrt() { }

  public Double sqrt(int n) {
    Double sum = new Double(0);

    for (int i = 0; i < n; i += 1) {
      sum += Double.valueOf(Math.sqrt((double) i));
    }

    return sum;
  }
}
