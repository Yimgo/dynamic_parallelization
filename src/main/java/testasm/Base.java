package testasm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.multiverse.api.references.*;
import org.multiverse.api.collections.*;
import static org.multiverse.api.StmUtils.*;
import static org.multiverse.api.GlobalStmInstance.getGlobalStmInstance;

import org.pmw.tinylog.Logger;

public class Base {
  public ExecutorService pool;
  public Base(ExecutorService pool) {
    this.pool = pool;
  }
  public void doRun() throws Throwable {
    Integer count = 10000;
    Future<Integer>[] futures = new Future[count];
    Transaction[] transactions = new Transaction[count];
    List<Integer> integers = new ArrayList<Integer>(IntStream.range(0, count).boxed().collect(Collectors.toList()));
    Integer sum = 0;
    Integer i = 0;
    List<Object> frame = new ArrayList<Object>();

    frame.add(this);
    frame.add(count);
    frame.add(integers);
    frame.add(sum);
    frame.add(i);

    SpeculativeList speculativeFrame = new SpeculativeList(frame, count);

    int done = -1, retries = 0;

    while (done < count - 1 && retries < 10) {
      for (int j = done + 1; j < count; j += 1) {
        Class<?> innerClass = ClassLoader.getSystemClassLoader().loadClass("testasm.Base$Inner");
        Callable<Integer> innerInstance = (Callable) innerClass.getConstructor(Class.forName("java.lang.Integer"), Class.forName("testasm.SpeculativeList")).newInstance(Integer.valueOf(j), speculativeFrame);
        futures[j] = pool.submit(innerInstance);
      }

      for (int j = 0; j < count; j += 1) {
        int ret = futures[j].get();
        if (ret < j) {
          Logger.trace("Respawning threads from {0} to {1}.", done + 1, count);
          //return;
          done = ret;
          for (int k = done + 1; k < count; k += 1) {
            try {
              futures[k].cancel(true);
              futures[k].get();
            } catch (Throwable t) {
              // do nothing
            }
            futures[k] = null;
          }
          retries += 1;
          speculativeFrame.clearVectors();
          speculativeFrame.rollback(done);
          break;
        } else {
          done = j;
        }
      }
    }

    int expected = count;
    if (((Integer) speculativeFrame.list().get(4)).intValue() == expected) {
      Logger.trace("OK");
    } else {
      Logger.trace("NOK: got {0} while expected {1}", frame.get(4), expected);
    }
  }
}

class Base$Inner implements Callable<Integer> {
  private Integer id;
  private SpeculativeList frame;
  public Base$Inner(Integer id, SpeculativeList f) {
    this.id = id;
    frame = f;
  }
  public Integer call() {
    try {
      frame.speculativeStore(id, 4, ((Integer) frame.speculativeLoad(id, 4)) + 1);
      return id;
    } catch (Throwable t) {
      Logger.trace("Fiber {0} failed.", id);
      //Logger.trace(t);
      return id - 1;
    }
  }
}
