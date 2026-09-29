package ru.nerower.collector;

import java.util.ArrayList;
import java.util.List;
import ru.nerower.data.Snapshot;
import ru.nerower.data.ThreadBuffers;

public class PlainMetricsCollectorImpl implements MetricsCollector, AutoCloseable {

    private static final int BUCKETS_COUNT = 256;
    private static final int BUCKET_SIZE = 4;

    private final long[] globalBuckets = new long[BUCKETS_COUNT];
    private long globalCount;
    private long globalSum;
    private long globalMin;
    private long globalMax;

    private final List<ThreadBuffers> allStates = new ArrayList<>();
    private final Object snapshotLock = new Object();

    private final ThreadLocal<ThreadBuffers> myState = ThreadLocal.withInitial(() -> {
        ThreadBuffers s = new ThreadBuffers(BUCKETS_COUNT);
        synchronized (snapshotLock) {
            allStates.add(s);
        }
        return s;
    });

    volatile int globalActiveBuffer = 0;


    @Override
    public void record(long value) {
        ThreadBuffers buffers = getLocalBuffers();

        int activeBufferId;
        while (true) {
            activeBufferId = globalActiveBuffer;
            buffers.getInside().set(activeBufferId);
            if (globalActiveBuffer == activeBufferId) {
                break;
            }
            buffers.getInside().setRelease(ThreadBuffers.BufferType.NOWHERE.value);
        }

        int bucketId = Math.min((int) (value / BUCKET_SIZE), BUCKETS_COUNT - 1);
        buffers.getBuckets()[activeBufferId][bucketId]++;

        buffers.getCount()[activeBufferId]++;
        buffers.getSum()[activeBufferId] += value;

        if (value < buffers.getMin()[activeBufferId]) {
            buffers.getMin()[activeBufferId] = value;
        }
        if (value > buffers.getMax()[activeBufferId]) {
            buffers.getMax()[activeBufferId] = value;
        }

        buffers.getInside().setRelease(ThreadBuffers.BufferType.NOWHERE.value);
    }

    @Override
    public Snapshot snapshot() {

        synchronized (snapshotLock) {
            int toReadBufId = globalActiveBuffer;
            globalActiveBuffer = 1 - toReadBufId;

            for (ThreadBuffers buffers : allStates) {
                while (buffers.getInside().get() == toReadBufId) {
                    Thread.onSpinWait();
                }

                for (int i = 0; i < globalBuckets.length; i++) {
                    globalBuckets[i] += buffers.getBuckets()[toReadBufId][i];
                }

                globalCount += buffers.getCount()[toReadBufId];
                globalSum += buffers.getSum()[toReadBufId];

                globalMin += Math.min(buffers.getMin()[toReadBufId], globalMin);
                globalMax += Math.max(buffers.getMax()[toReadBufId], globalMax);
            }

        }

        long p50 = computePercentile(globalBuckets, globalCount, 0.50);
        long p99 = computePercentile(globalBuckets, globalCount, 0.99);

        return new Snapshot(globalBuckets, globalCount, globalSum, globalMin, globalMax, p50, p99);

    }

    private ThreadBuffers getLocalBuffers() {
        return myState.get();
    }

    private long computePercentile(long[] buckets, long count, double percentile) {
        long limit = (long) (count * percentile);
        long currentCount = 0;

        for (long bucket : buckets) {
            currentCount += bucket;
            if (currentCount >= limit) {
                break;
            }
        }

        return limit * BUCKET_SIZE;
    }

    @Override
    public void close() {
        myState.remove();
    }
}
