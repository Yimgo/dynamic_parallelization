package fr.yimgo.testasm;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class ParallelSqrt {

    static protected ExecutorService pool;

    public static Double parallel_sqrt(int n) throws Throwable {
        Double sum = new Double(0);
        List<Future<Double>> futures = new ArrayList<Future<Double>>();

        for (int i = 0; i < n; i += 1) {
            final int base = i;
            futures.add(pool.submit(new Callable<Double> () {
                public Double call() throws Exception {
                    return Double.valueOf(Math.sqrt((double) base));
                }
            }));
        }

        for (Future<Double> future : futures){
            sum += future.get();
        }

        return sum;
    }

    public static void main(String... args) throws Throwable {
        pool = Executors.newFixedThreadPool(2);
        System.out.println(parallel_sqrt(new Integer(args[0])));
        pool.shutdown();
    }
}
