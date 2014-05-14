package fr.yimgo.testasm;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class ParallelSqrt {
    public ParallelSqrt() { }
    public Double sqrt(int n, ExecutorService pool) throws Throwable {
        Double sum = new Double(0);
        List<Future<Double>> futures = new ArrayList<Future<Double>>();

        for (int i = 0; i < n; i += 1) {
            Class<?> inner = ClassLoader.getSystemClassLoader().loadClass("fr.yimgo.testasm.ParallelSqrtInner");
            Callable innerInstance = (Callable) inner.getConstructor(int.class).newInstance(i);
            futures.add(pool.submit(innerInstance));
        }

        for (Future<Double> future : futures){
            sum += future.get();
        }

        return sum;
    }
}

class ParallelSqrtInner implements Callable<Double> {
    final private int val;
    public ParallelSqrtInner(int v) {
        val = v;
    }
    public Double call() throws Exception {
        return Double.valueOf(Math.sqrt((double) val));
    }
}
