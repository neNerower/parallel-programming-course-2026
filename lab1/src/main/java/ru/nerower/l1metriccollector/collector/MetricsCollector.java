package ru.nerower.l1metriccollector.collector;


import ru.nerower.l1metriccollector.data.Snapshot;

public interface MetricsCollector {
    void record(long value);
    Snapshot snapshot();
}
