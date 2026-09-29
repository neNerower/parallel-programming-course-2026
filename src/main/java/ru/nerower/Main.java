package ru.nerower;

import ru.nerower.collector.MetricsCollector;
import ru.nerower.collector.PlainMetricsCollectorImpl;
import ru.nerower.util.BunchRunner;
import ru.nerower.util.CipfLoadGenerator;
import ru.nerower.util.ConsistencyRunner;

public class Main {

    private static final int THREAD_COUNT = 8;
    private static final int REQ_COUNT = 1 << 20;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Start point");
        MetricsCollector collector = new PlainMetricsCollectorImpl();
        int[] values = CipfLoadGenerator.defaultGenerator().generateLoad(REQ_COUNT);
        System.out.println("Request values generated");

        new ConsistencyRunner().measurePoint(collector, values, THREAD_COUNT);

        long median = new BunchRunner().measurePoint(collector, values, THREAD_COUNT);
        System.out.println("Median: " + median);
    }

}