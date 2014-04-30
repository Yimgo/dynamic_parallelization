package fr.yimgo.testasm;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class ParallelSqrt {

    static protected ExecutorService pool;

    public static double parallel_sqrt(int n) throws Throwable {
        double sum = 0;
        List<Future<Double>> futures = new ArrayList<Future<Double>>();

        for (int i = 0; i < n; i += 1) {
            final int base = i;
            futures.add(pool.submit(new Callable<Double>() {
                public Double call() {
                    return Math.sqrt(base);
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
        //System.out.println(parallel_sqrt(new Integer(args[0])));
        long computation_time = System.nanoTime();
        parallel_sqrt(new Integer(args[0]));
        computation_time = System.nanoTime() - computation_time;
        System.out.println(computation_time);
        pool.shutdown();
    }
}
