package fr.yimgo.testasm;

public class SequentialSqrt {

  public static Double sequential_sqrt(int n) {
    Double sum = new Double(0);

    for (int i = 0; i < n; i += 1) {
      sum += Double.valueOf(Math.sqrt((double) i));
    }

    return sum;
  }

  public static void main(String... args) throws Throwable {
    System.out.println(sequential_sqrt(new Integer(args[0])));
  }
}
