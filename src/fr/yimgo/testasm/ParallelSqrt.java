package fr.yimgo.testasm;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.CopyOnWriteArrayList;

public class ParallelSqrt {
  public ExecutorService pool;
  public ParallelSqrt() { }
  public Double sqrt(int n) throws Throwable {
    Double sum = 0.0;
    List<Future<?>> futures = new ArrayList<Future<?>>();

    Integer i = 0;
    List<Object> frame = new CopyOnWriteArrayList<Object>();
    frame.add(this);
    frame.add(new Integer(n));
    frame.add(sum);
    frame.add(futures);
    frame.add(i);
    for (; i < n; i += 1) {
      Class<?> inner = ClassLoader.getSystemClassLoader().loadClass("fr.yimgo.testasm.ParallelSqrtInner");
      Runnable innerInstance = (Runnable) inner.getConstructor(Class.forName("java.util.List")).newInstance(frame);
      futures.add(pool.submit(innerInstance));
    }

    for (Future<?> future : futures){
      future.get();
    }

    sum = (Double) frame.get(2);

    return sum;
  }
}

class ParallelSqrtInner implements Runnable {
  private List<Object> frame;
  public ParallelSqrtInner(List<Object> f) {
    frame = new CopyOnWriteArrayList(f);
  }
  public void run() {
    synchronized (frame) {
      //frame.set(2, ((Double) frame.get(2)) + Math.sqrt(((Integer) frame.get(4))));
      frame.set(4, ((Integer) frame.get(4)) + 1);
    }
  }
}
