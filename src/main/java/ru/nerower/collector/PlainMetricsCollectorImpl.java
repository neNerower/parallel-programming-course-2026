package ru.nerower.collector;

import ru.nerower.data.Snapshot;

public class PlainMetricsCollectorImpl implements MetricsCollector {

    private static final int BUCKETS_COUNT = 256;
    private static final int BUCKET_SIZE = 4;

    private final long[] buckets = new long[BUCKETS_COUNT];
    private long count;
    private long sum;
    private long min;
    private long max;

    @Override
    public void record(long value) {
        buckets[Math.min((int) (value / BUCKET_SIZE), BUCKETS_COUNT - 1)]++;
        count++;
        sum += value;
        min = Math.min(min, value);
        max = Math.max(max, value);
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(
            buckets,
            count(),
            sum(),
            min(),
            max(),
            percentile(50),
            percentile(99)
        );
    }

    private long count() {
        return count;
    }

    private long sum() {
        return sum;
    }

    private long min() {
        return min;
    }

    private long max() {
        return max;
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
