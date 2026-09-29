package ru.nerower.util;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import ru.nerower.collector.MetricsCollector;
import ru.nerower.data.Snapshot;

public class ConsistencyRunner {

    private static final int SNAPSHOT_REQ_COUNT = 10_000;

    /**
     * Вычисляет скорость обработки запросов
     */
    public double run(MetricsCollector collector, int[] values, int threadsCount, int limit)
        throws InterruptedException {

        CountDownLatch startSignal = new CountDownLatch(1);
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        long[] opsCounts = new long[threadsCount];


        Thread[] threads = new Thread[threadsCount];
        for (int k = 0; k < threadsCount; k++) {
            int idx = k;

            threads[k] = Thread.startVirtualThread(() -> {
                int localCount = 0;
                int i = idx * 1000;

                try {
                    startSignal.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                while (!stopFlag.get()) {
                    localCount++;
                    collector.record(values[i++]);

                    if (i == values.length) {
                        i = 0;
                    }
                }

                opsCounts[idx] = localCount;
            });
        }

        startSignal.countDown();


        long[] consistGist = new long[3];
        for (int i = 0; i < limit; i++) {
            Snapshot snapshot = collector.snapshot();
            int compRes = Long.compare(Arrays.stream(snapshot.buckets()).sum(), snapshot.count());
            if (compRes > 0)  consistGist[2]++;
            if (compRes < 0)  consistGist[0]++;
            if (compRes == 0) consistGist[1]++;
        }
        stopFlag.set(true);

        long all = consistGist[0] + consistGist[1] + consistGist[2];
        long wrong = consistGist[0] + consistGist[2];
        System.out.println("Процент меньших: " + (double) consistGist[0] / all);
        System.out.println("Процент равных: " + (double) consistGist[1] / all);
        System.out.println("Процент больших: " + (double) consistGist[2] / all);

        double inaccuracy = (double) wrong / all;
        System.out.println("Inaccuracy: " + inaccuracy);


        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("Запросов накидали потоки: " + Arrays.stream(opsCounts).sum());
        System.out.println("Запросов насчитал коллектор: " + Arrays.stream(opsCounts).sum());


        return inaccuracy;
    }

    public double measurePoint(MetricsCollector collector, int[] values, int threadsCount) throws InterruptedException {
        return run(collector, values, threadsCount, SNAPSHOT_REQ_COUNT);
    }

}
