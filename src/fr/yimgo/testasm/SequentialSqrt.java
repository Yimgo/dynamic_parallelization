package fr.yimgo.testasm;

import java.util.List;
import java.util.ArrayList;

public class SequentialSqrt {

  public static double sequential_sqrt(int n) {
    double sum = 0;

    for (int i = 0; i < n; i += 1) {
      sum += Math.sqrt(i);
    }

    return sum;
  }

  public static void main(String... args) throws Throwable {
    System.out.println(sequential_sqrt(new Integer(args[0])));
    long computation_time = System.nanoTime();
    sequential_sqrt(new Integer(args[0]));
    computation_time = System.nanoTime() - computation_time;
    System.out.println(computation_time);
  }
}
