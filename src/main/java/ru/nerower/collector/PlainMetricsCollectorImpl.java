package ru.nerower.collector;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import ru.nerower.data.Snapshot;

public class PlainMetricsCollectorImpl implements MetricsCollector {

    private static final int BUCKETS_COUNT = 256;
    private static final int BUCKET_SIZE = 4;
    private static final int LOCK_STRIPES_COUNT = 16;

    private final long[] buckets;
    private final ReentrantLock[] stripedLocks;

    private final AtomicLong count = new AtomicLong(0);
    private final AtomicLong sum = new AtomicLong(0);
    private final AtomicLong min = new AtomicLong(0);
    private final AtomicLong max = new AtomicLong(0);

    public PlainMetricsCollectorImpl() {
        this.buckets = new long[BUCKETS_COUNT];
        this.stripedLocks = Stream.generate(ReentrantLock::new)
            .limit(LOCK_STRIPES_COUNT)
            .toArray(ReentrantLock[]::new);
    }

    @Override
    public void record(long value) {
        int bucketId = Math.min((int) (value / BUCKET_SIZE), BUCKETS_COUNT - 1);
        stripedLocks[bucketId % LOCK_STRIPES_COUNT].lock();
        buckets[bucketId]++;
        stripedLocks[bucketId % LOCK_STRIPES_COUNT].unlock();

        count.incrementAndGet();
        sum.addAndGet(value);

        long currentMin;
        do {
            currentMin = min.get();
            if (value >= currentMin) {
                break;
            }
        } while (min.compareAndSet(currentMin, value));

        long currentMax;
        do {
            currentMax = max.get();
            if (value <= currentMax) {
                break;
            }
        } while (max.compareAndSet(currentMax, value));

//        min.updateAndGet(prev -> Math.min(prev, value));
//        max.updateAndGet(prev -> Math.max(prev, value));
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(
            buckets(),
            count(),
            sum(),
            min(),
            max(),
            percentile(50),
            percentile(99)
        );
    }

    private long[] buckets() {
        long[] snapBuckets = new long[buckets.length];
        for (int groupId = 0; groupId < LOCK_STRIPES_COUNT; groupId++) {
            stripedLocks[groupId].lock();

            for (int bucketId = groupId; bucketId < BUCKETS_COUNT; bucketId += LOCK_STRIPES_COUNT) {
                snapBuckets[bucketId] = buckets[bucketId];
            }

            stripedLocks[groupId].unlock();
        }
        return snapBuckets;
    }

    private long count() {
        return count.longValue();
    }

    private long sum() {
        return sum.longValue();
    }

    private long min() {
        return min.longValue();
    }

    private long max() {
        return max.longValue();
    }

    private long percentile(int percentile) {
        long limit = count() * percentile;
        long currentCount = 0;

        for (long bucket : buckets) {
            currentCount += bucket;
            if (currentCount >= limit) {
                break;
            }
        }

        return limit * BUCKET_SIZE;
    }

}
