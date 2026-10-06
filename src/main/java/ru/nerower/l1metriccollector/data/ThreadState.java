package ru.nerower.l1metriccollector.data;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import lombok.Getter;

@Getter
public final class ThreadState {

    private final AtomicLongArray buckets;
    private final AtomicLong count;
    private final AtomicLong sum;
    private final AtomicLong min;
    private final AtomicLong max;

    public ThreadState(int bucketCount) {
        buckets = new AtomicLongArray(bucketCount);
        count = new AtomicLong();
        sum = new AtomicLong();
        min = new AtomicLong(Long.MAX_VALUE);
        max = new AtomicLong(0);
    }

}
