package ru.nerower.util;

import java.util.Random;
import java.util.stream.IntStream;

public class CipfLoadGenerator {

    private static final long SEED = 22;
    private static final int MAX_REQ_TIME = 1023;
    private static final float EXPONENT = 1.15f;

    private final double[] cdf; // cdf[k-1] = P(X <= k)

    public static CipfLoadGenerator defaultGenerator() {
        return new CipfLoadGenerator(MAX_REQ_TIME, EXPONENT);
    }

    public CipfLoadGenerator(int maxK, double exponent) {
        double sum = 0;
        for (int k = 1; k <= maxK; k++) {
            sum += 1.0 / Math.pow(k, exponent);
        }
        cdf = new double[maxK];
        double acc = 0;
        for (int k = 1; k <= maxK; k++) {
            acc += 1.0 / Math.pow(k, exponent) / sum;
            cdf[k - 1] = acc;
        }
    }

    private int sample(Random rnd) {
        double u = rnd.nextDouble(); // [0, 1)
        int lo = 0, hi = cdf.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cdf[mid] < u) lo = mid + 1;
            else hi = mid;
        }
        return lo + 1; // k
    }

    public int[] generateLoad(int reqCount) {
        Random rnd = new Random(SEED);
        return IntStream.generate(() -> sample(rnd))
            .limit(reqCount)
            .toArray();
    }

}
