package ru.nerower.l1metriccollector.data;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;

@Getter
public final class ThreadBuffers {

    private static final int BUFF_COUNT = 2;

    // Два буфера: [0] и [1]. Обычные массивы и переменные!
    final long[][] buckets;
    final long[] count;
    final long[] sum;
    final long[] min;
    final long[] max;
    // Флаг входа: -1 = вне буферов, 0 = запись в буфер 0, 1 = запись в буфер 1
    final AtomicInteger inside;

    public ThreadBuffers(int bucketCount) {
        buckets = new long[BUFF_COUNT][bucketCount];
        count = new long[BUFF_COUNT];
        sum = new long[BUFF_COUNT];
        min = new long[] {Long.MAX_VALUE, Long.MAX_VALUE};
        max = new long[] {0, 0};
        inside = new AtomicInteger(BufferType.NOWHERE.value);
    }

    public void drop(int bufferId) {
        Arrays.fill(buckets[bufferId], 0);
        count[bufferId] = 0;
        sum[bufferId] = 0;
        min[bufferId] = Long.MAX_VALUE;
        max[bufferId] = 0;
    }


    public enum BufferType {
        NOWHERE(-1), LEFT(0), RIGHT(1);

        public final int value;

        BufferType(int value) {
            this.value = value;
        }
    }

}


