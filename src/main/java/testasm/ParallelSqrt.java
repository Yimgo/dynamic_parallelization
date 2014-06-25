package testasm;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.CopyOnWriteArrayList;

import org.multiverse.api.references.*;
import static org.multiverse.api.StmUtils.*;

public class ParallelSqrt {
  public ParallelSqrt() { }
  public Double sqrt(int n) throws Throwable {
    Double sum = 0.0;
    List<Future<?>> futures = new ArrayList<Future<?>>();

    Integer i = 0;
    List<Object> frame = new ArrayList<Object>();
    frame.add(this);
    frame.add(new Integer(n));
    frame.add(sum);
    frame.add(futures);
    frame.add(i);
    for (; (Integer)frame.get(4) < n;) {
      Class<?> inner = ClassLoader.getSystemClassLoader().loadClass("fr.yimgo.testasm.ParallelSqrtInner");
      Runnable innerInstance = (Runnable) inner.getConstructor(Class.forName("java.util.List")).newInstance(frame);
      atomic(innerInstance);
    }

    return sum;
  }
}

class ParallelSqrtInner implements Runnable {
  private List<Object> frame;
  public ParallelSqrtInner(List<Object> f) {
    frame = f;
  }
  public void run() {
    System.out.println((Integer)frame.get(4));
    frame.set(4, (Integer)frame.get(4) + 1);
  }
}
