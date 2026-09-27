package ru.nerower;

import ru.nerower.collector.MetricsCollector;
import ru.nerower.collector.PlainMetricsCollectorImpl;
import ru.nerower.util.BunchRunner;
import ru.nerower.util.CipfLoadGenerator;

public class Main {

    private static final int THREAD_COUNT = 1;
    private static final int REQ_COUNT = 1 << 20;

    public static void main(String[] args) throws InterruptedException {
        MetricsCollector collector = new PlainMetricsCollectorImpl();

        int[] values = CipfLoadGenerator.defaultGenerator().generateLoad(REQ_COUNT);
        long median = new BunchRunner().measurePoint(collector, values, THREAD_COUNT);
        System.out.println("Median: " + median);
    }




}