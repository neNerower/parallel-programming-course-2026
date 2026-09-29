package ru.nerower.collector;

import java.util.ArrayList;
import java.util.List;
import ru.nerower.data.Snapshot;
import ru.nerower.data.ThreadState;

public class PlainMetricsCollectorImpl implements MetricsCollector, AutoCloseable {

    private static final int BUCKETS_COUNT = 256;
    private static final int BUCKET_SIZE = 4;

    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();

    private final ThreadLocal<ThreadState> myState = ThreadLocal.withInitial(() -> {
        ThreadState s = new ThreadState(BUCKETS_COUNT);
        synchronized (listLock) {
            allStates.add(s);
        }
        return s;
    });

    @Override
    public void record(long value) {
        ThreadState state = getLocalState();

        int bucketId = Math.min((int) (value / BUCKET_SIZE), BUCKETS_COUNT - 1);
        state.getBuckets().setRelease(bucketId, state.getBuckets().getPlain(bucketId) + 1);

        state.getCount().setRelease(state.getCount().getPlain() + 1);
        state.getSum().setRelease(state.getSum().getPlain() + value);

        if (value < state.getMin().getPlain()) {
            state.getMin().setRelease(value);
        }
        if (value > state.getMax().getPlain()) {
            state.getMax().setRelease(value);
        }
    }

    @Override
    public Snapshot snapshot() {
        List<ThreadState> copyOfStates = new ArrayList<>(allStates);

        long[] out = new long[256];
        long count = 0;
        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = 0;

        for (ThreadState s : copyOfStates) {
            for (int i = 0; i < 256; i++) {
                out[i] += s.getBuckets().get(i);
            }
            count += s.getCount().get();
            sum   += s.getSum().get();
            min    = Math.min(min, s.getMin().get());
            max    = Math.max(max, s.getMax().get());
        }

        long p50 = computePercentile(out, count, 0.50);
        long p99 = computePercentile(out, count, 0.99);

        return new Snapshot(out, count, sum, min, max, p50, p99);

    }

    private ThreadState getLocalState() {
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
