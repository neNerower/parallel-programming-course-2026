package ru.nerower.l1metriccollector.util;

import static java.time.LocalTime.now;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import ru.nerower.l1metriccollector.collector.MetricsCollector;

public class BunchRunner {

    /**
     * Вычисляет скорость обработки запросов
     */
    public long run(MetricsCollector collector, int[] values, int threadsCount, int seconds)
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

        LocalTime t0 = now();
        startSignal.countDown();
        Thread.sleep(seconds * 1_000L);
        stopFlag.set(true);
        LocalTime t1 = now();

        for (Thread thread : threads) {
            thread.join();
        }

        return Arrays.stream(opsCounts).sum() / (t1.getSecond() - t0.getSecond());
    }

    public long measurePoint(MetricsCollector collector, int[] values, int threadsCount) throws InterruptedException {

        final int RUNNING_SEC = 5;
        final int RUNS_COUNT = 5;
        run(collector, values, threadsCount, RUNNING_SEC);

        long[] speeds = new long[RUNS_COUNT];
        for (int i = 0; i < RUNS_COUNT; i++) {
            System.out.println("Run " + i + " of " + RUNS_COUNT);
            speeds[i] = run(collector, values, threadsCount, RUNNING_SEC);
            System.out.println(collector.snapshot().count());
        }

        Arrays.sort(speeds);
        // TODO медиана для четного кол-ва
        return speeds[RUNS_COUNT / 2];
    }

}
