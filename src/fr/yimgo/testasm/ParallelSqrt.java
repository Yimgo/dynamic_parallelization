package fr.yimgo.testasm;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ParallelSqrt {
    public ExecutorService pool;
    public ParallelSqrt() { }
    public Double sqrt(int n) throws Throwable {
        Double sum = new Double(0);
        List<Future<?>> futures = new ArrayList<Future<?>>();

        for (int i = 0; i < n; i += 1) {
            Class<?> inner = ClassLoader.getSystemClassLoader().loadClass("fr.yimgo.testasm.ParallelSqrtInner");
            List<Object> frame = new ArrayList<Object>();
            frame.add(this);
            frame.add(new Integer(n));
            frame.add(sum);
            frame.add(futures);
            frame.add(new Integer(i));
            Runnable innerInstance = (Runnable) inner.getConstructor(Class.forName("java.util.List")).newInstance(frame);
            futures.add(pool.submit(innerInstance));
        }

        for (Future<?> future : futures){
            future.get();
        }

        return sum;
    }
}

class ParallelSqrtInner implements Runnable {
    final private List<Object> frame;
    public ParallelSqrtInner(List<Object> f) {
        frame = f;
    }
    public void run() {
        frame.set(2, ((Double) frame.get(2)) + ((Integer) frame.get(4)));
    }
}
