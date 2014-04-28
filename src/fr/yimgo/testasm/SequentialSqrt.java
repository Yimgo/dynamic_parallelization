package fr.yimgo.testasm;

import java.util.List;
import java.util.ArrayList;

public class SequentialSqrt {

  public static List<Double> sequential_sqrt(int n) {
    List<Double> list = new ArrayList<Double>();

    for (int i = 0; i < n; i += 1) {
      list.add(Math.sqrt(i));
    }

    return list;
  }

  public static void main(String... args) throws Throwable {
    //System.out.println(sequential_sqrt(new Integer(args[0])));
    long computation_time = System.nanoTime();
    sequential_sqrt(new Integer(args[0]));
    computation_time = System.nanoTime() - computation_time;
    System.out.println(computation_time);
  }
}
