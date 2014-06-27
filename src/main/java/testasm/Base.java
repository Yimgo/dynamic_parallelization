package testasm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.multiverse.api.references.*;
import static org.multiverse.api.StmUtils.*;

import org.pmw.tinylog.Logger;

public class Base {
  public ExecutorService pool;
  public Base(ExecutorService pool) {
    this.pool = pool;
  }
  public void doRun() throws Throwable {
    List<Object> frame = new ArrayList<Object>();
    Integer count = 100000;
    List<Integer> integers = new ArrayList<Integer>(IntStream.range(0, count).boxed().collect(Collectors.toList()));
    Integer sum = 0;
    Integer i = 0;
    frame.add(this);
    frame.add(count);
    frame.add(integers);
    frame.add(sum);
    frame.add(i);

    for (; i < count; ++i) {
      Class<?> innerClass = ClassLoader.getSystemClassLoader().loadClass("testasm.Base$Proxy");
      Runnable innerInstance = (Runnable) innerClass.getConstructor(Class.forName("java.util.List")).newInstance(frame);
      atomic(innerInstance);
      //Class<?> innerClass = ClassLoader.getSystemClassLoader().loadClass("testasm.Base$Inner");
      //Runnable innerInstance = (Runnable) innerClass.getConstructor(Class.forName("java.util.List")).newInstance(frame);
      //pool.submit(innerInstance);
    }

    int expected = (count - 1) * count / 2;
    if (((Integer) frame.get(3)).intValue() == expected) {
      Logger.trace("OK");
    } else {
      Logger.trace("NOK: got {0} while expected {1}", frame.get(3), expected);
    }
  }
}

class Base$Proxy implements Runnable {
  private List<Object> frame;
  public Base$Proxy(List<Object> f) {
    frame = f;
  }
  public void run() {
    try {
      Class<?> innerClass = ClassLoader.getSystemClassLoader().loadClass("testasm.Base$Inner");
      Runnable innerInstance = (Runnable) innerClass.getConstructor(Class.forName("java.util.List")).newInstance(frame);
      atomic(innerInstance);
    } catch(Throwable t) {
      Logger.error(t);
    }
  }
}

class Base$Inner implements Runnable {
  private List<Object> frame;
  public Base$Inner(List<Object> f) {
    frame = f;
  }
  public void run() {
    if ((Integer) frame.get(4) < (Integer) frame.get(1)) {
      frame.set(3, (Integer) frame.get(3) + ((List<Integer>) frame.get(2)).get((Integer) frame.get(4)));
      frame.set(4, (Integer) frame.get(4) + 1);
    }
  }
}
