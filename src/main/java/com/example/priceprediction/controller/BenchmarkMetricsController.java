package com.example.priceprediction.controller;

import com.example.priceprediction.service.SteamApiCallMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkMetricsController {

    private final SteamApiCallMetrics steamApiCallMetrics;

    public BenchmarkMetricsController(SteamApiCallMetrics steamApiCallMetrics) {
        this.steamApiCallMetrics = steamApiCallMetrics;
    }

    @GetMapping("/steam-api-metrics")
    public Map<String, Long> steamApiMetrics(@RequestParam(defaultValue = "false") boolean reset) {
        if (reset) {
            SteamApiCallMetrics.Snapshot snapshot = steamApiCallMetrics.reset();
            return Map.of(
                    "steamApiTotalCalls", snapshot.totalCalls(),
                    "steamApiPeakCallsPerSecond", snapshot.peakCallsPerSecond()
            );
        }
        return steamApiCallMetrics.snapshotAsMap();
    }
}
