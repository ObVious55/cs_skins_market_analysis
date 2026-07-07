package com.example.priceprediction.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SteamApiCallMetrics {

    private final AtomicLong totalCalls = new AtomicLong();
    private final ConcurrentHashMap<Long, AtomicLong> callsByEpochSecond = new ConcurrentHashMap<>();

    public void recordInventoryApiCall() {
        totalCalls.incrementAndGet();
        long epochSecond = Instant.now().getEpochSecond();
        callsByEpochSecond.computeIfAbsent(epochSecond, ignored -> new AtomicLong()).incrementAndGet();
    }

    public Snapshot snapshot() {
        long peakCallsPerSecond = callsByEpochSecond.values()
                .stream()
                .mapToLong(AtomicLong::get)
                .max()
                .orElse(0L);
        return new Snapshot(totalCalls.get(), peakCallsPerSecond);
    }

    public Snapshot reset() {
        Snapshot snapshot = snapshot();
        totalCalls.set(0);
        callsByEpochSecond.clear();
        return snapshot;
    }

    public Map<String, Long> snapshotAsMap() {
        Snapshot snapshot = snapshot();
        return Map.of(
                "steamApiTotalCalls", snapshot.totalCalls(),
                "steamApiPeakCallsPerSecond", snapshot.peakCallsPerSecond()
        );
    }

    public record Snapshot(long totalCalls, long peakCallsPerSecond) {
    }
}
