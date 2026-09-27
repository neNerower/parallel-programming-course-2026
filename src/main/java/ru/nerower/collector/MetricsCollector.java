package ru.nerower.collector;


import ru.nerower.data.Snapshot;

public interface MetricsCollector {
    void record(long value);
    Snapshot snapshot();
}
