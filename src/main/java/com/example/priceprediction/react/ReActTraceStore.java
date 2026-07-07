package com.example.priceprediction.react;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ReActTraceStore {

    private final Map<String, List<ReActTraceRecord>> traces = new ConcurrentHashMap<>();

    public void clear(String traceId) {
        traces.remove(traceId);
    }

    public void record(ReActTraceRecord record) {
        if (record == null || record.getTraceId() == null || record.getTraceId().isBlank()) {
            return;
        }
        traces.computeIfAbsent(record.getTraceId(), ignored -> new ArrayList<>()).add(record);
    }

    public List<ReActTraceRecord> findByTraceId(String traceId) {
        return traces.getOrDefault(traceId, List.of())
                .stream()
                .sorted(Comparator.comparingInt(ReActTraceRecord::getStep))
                .toList();
    }
}
