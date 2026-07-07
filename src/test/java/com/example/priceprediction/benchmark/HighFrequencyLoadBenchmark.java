package com.example.priceprediction.benchmark;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

/**
 * Standalone pressure-test runner for the high-frequency Redis/Kafka/AI paths.
 *
 * <p>Example:
 * mvn -DskipTests test-compile exec:java \
 *   -Dexec.classpathScope=test \
 *   -Dexec.mainClass=com.example.priceprediction.benchmark.HighFrequencyLoadBenchmark \
 *   -Dexec.args="--scenario inventory-refresh --requests 1000 --concurrency 50"
 */
public final class HighFrequencyLoadBenchmark {

    private HighFrequencyLoadBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        if (config.help) {
            Config.printHelp();
            return;
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.connectTimeoutSeconds))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        if (config.scenario == Scenario.ALL) {
            for (Scenario scenario : List.of(
                    Scenario.USER_INFO,
                    Scenario.INVENTORY_READ,
                    Scenario.INVENTORY_REFRESH,
                    Scenario.CHAT
            )) {
                runScenario(client, config.withScenario(scenario));
                System.out.println();
            }
            return;
        }

        runScenario(client, config);
    }

    private static void runScenario(HttpClient client, Config config) throws Exception {
        List<String> steamIds = config.steamIds();
        ScenarioWorkload workload = ScenarioWorkload.create(config, steamIds);

        if (config.warmupRequests > 0) {
            execute(client, workload, config.withRequests(config.warmupRequests), true, 0, "warmup");
        }

        ObservabilityProbe probe = ObservabilityProbe.create(client, config);
        probe.beforeBenchmark();
        LocalDateTime benchmarkStartedAt = LocalDateTime.now().minusSeconds(1);
        KafkaLagSnapshot kafkaLagBefore = probe.kafkaLag();
        BenchmarkResult result = execute(client, workload, config, false, config.warmupRequests, "main");
        KafkaLagSnapshot kafkaLagAfterSubmit = probe.kafkaLag();
        CompletionSnapshot completion = probe.waitForCompletion(
                benchmarkStartedAt,
                result.successfulRequestIds(),
                config.completionWaitSeconds
        );
        AppMetricsSnapshot appMetrics = probe.appMetrics();
        result.print(config, new ObservabilityReport(kafkaLagBefore, kafkaLagAfterSubmit, completion, appMetrics));
    }

    private static BenchmarkResult execute(
            HttpClient client,
            ScenarioWorkload workload,
            Config config,
            boolean warmup,
            int sequenceOffset,
            String phase
    ) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(config.concurrency);
        CompletionService<RequestResult> completionService = new ExecutorCompletionService<>(executor);
        AtomicInteger sequence = new AtomicInteger(sequenceOffset);

        long startedAt = System.nanoTime();
        for (int i = 0; i < config.requests; i++) {
            completionService.submit(new HttpTask(client, workload, config, sequence, phase));
        }

        List<RequestResult> results = new ArrayList<>(config.requests);
        for (int i = 0; i < config.requests; i++) {
            try {
                results.add(completionService.take().get());
            } catch (Exception e) {
                results.add(RequestResult.failed(0, e, null));
            }
        }
        long elapsedNanos = System.nanoTime() - startedAt;

        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }

        if (warmup) {
            System.out.printf(
                    Locale.ROOT,
                    "Warmup finished: scenario=%s, requests=%d, elapsed=%.2fs%n",
                    config.scenario.wireName,
                    config.requests,
                    elapsedNanos / 1_000_000_000.0
            );
        }
        return new BenchmarkResult(results, elapsedNanos);
    }

    private record HttpTask(
            HttpClient client,
            ScenarioWorkload workload,
            Config config,
            AtomicInteger sequence,
            String phase
    ) implements Callable<RequestResult> {

        @Override
        public RequestResult call() {
            int requestNo = sequence.getAndIncrement();
            RequestSpec requestSpec = workload.buildRequest(requestNo, phase);
            long startedAt = System.nanoTime();
            try {
                HttpResponse<String> response = client.send(
                        requestSpec.request(),
                        HttpResponse.BodyHandlers.ofString()
                );
                long latencyNanos = System.nanoTime() - startedAt;
                return RequestResult.success(response.statusCode(), latencyNanos, requestSpec.requestId());
            } catch (IOException e) {
                long latencyNanos = System.nanoTime() - startedAt;
                return RequestResult.failed(latencyNanos, e, requestSpec.requestId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                long latencyNanos = System.nanoTime() - startedAt;
                return RequestResult.failed(latencyNanos, e, requestSpec.requestId());
            } catch (RuntimeException e) {
                long latencyNanos = System.nanoTime() - startedAt;
                return RequestResult.failed(latencyNanos, e, requestSpec.requestId());
            }
        }
    }

    private static final class ScenarioWorkload {
        private final Config config;
        private final List<String> steamIds;

        private ScenarioWorkload(Config config, List<String> steamIds) {
            this.config = config;
            this.steamIds = steamIds;
        }

        static ScenarioWorkload create(Config config, List<String> steamIds) {
            return new ScenarioWorkload(config, steamIds);
        }

        RequestSpec buildRequest(int requestNo, String phase) {
            String steamId = selectSteamId(requestNo);
            return switch (config.scenario) {
                case USER_INFO -> new RequestSpec(get("/api/auth/user/info?steamId=" + url(steamId)), null);
                case INVENTORY_READ -> new RequestSpec(get("/api/inventory/me?steamId=" + url(steamId)), null);
                case INVENTORY_REFRESH -> {
                    String requestId = config.requestId(phase, requestNo);
                    yield new RequestSpec(post(
                            "/api/inventory/refresh?steamId=" + url(steamId) + "&requestId=" + url(requestId),
                            ""
                    ), requestId);
                }
                case CHAT -> new RequestSpec(postJson(
                        "/api/ai/chat",
                        """
                                {"message":"%s","steamId":"%s","followUp":%s}
                                """.formatted(json(config.messageFor(requestNo)), json(steamId), config.followUp)
                ), null);
                case ALL -> throw new IllegalStateException("ALL is expanded before workload execution");
            };
        }

        private String selectSteamId(int requestNo) {
            if (config.sameUser) {
                return steamIds.get(0);
            }
            if (config.randomUsers) {
                return steamIds.get(ThreadLocalRandom.current().nextInt(steamIds.size()));
            }
            return steamIds.get(requestNo % steamIds.size());
        }

        private HttpRequest get(String pathAndQuery) {
            return base(pathAndQuery).GET().build();
        }

        private HttpRequest post(String pathAndQuery, String body) {
            return base(pathAndQuery)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
        }

        private HttpRequest postJson(String pathAndQuery, String body) {
            return base(pathAndQuery)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
        }

        private HttpRequest.Builder base(String pathAndQuery) {
            return HttpRequest.newBuilder(URI.create(config.baseUrl + pathAndQuery))
                    .timeout(Duration.ofSeconds(config.requestTimeoutSeconds))
                    .header("Accept", "application/json");
        }
    }

    private record RequestSpec(HttpRequest request, String requestId) {
    }

    private record RequestResult(int statusCode, long latencyNanos, String error, String requestId) {
        static RequestResult success(int statusCode, long latencyNanos, String requestId) {
            return new RequestResult(statusCode, latencyNanos, null, requestId);
        }

        static RequestResult failed(long latencyNanos, Throwable error, String requestId) {
            String message = error == null ? "unknown" : error.getClass().getSimpleName() + ": " + error.getMessage();
            return new RequestResult(0, latencyNanos, message, requestId);
        }

        boolean ok() {
            return error == null && statusCode >= 200 && statusCode < 400;
        }
    }

    private record BenchmarkResult(List<RequestResult> results, long elapsedNanos) {
        long successCount() {
            return results.stream().filter(RequestResult::ok).count();
        }

        List<String> successfulRequestIds() {
            return results.stream()
                    .filter(RequestResult::ok)
                    .map(RequestResult::requestId)
                    .filter(Objects::nonNull)
                    .toList();
        }

        void print(Config config, ObservabilityReport observabilityReport) {
            List<Long> latencies = results.stream()
                    .filter(result -> result.latencyNanos > 0)
                    .map(RequestResult::latencyNanos)
                    .sorted()
                    .toList();

            long success = successCount();
            long failed = results.size() - success;
            double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
            double throughput = results.isEmpty() ? 0.0 : results.size() / elapsedSeconds;

            Map<Integer, Long> statusCounts = new HashMap<>();
            Map<String, Long> errorCounts = new HashMap<>();
            for (RequestResult result : results) {
                if (result.error == null) {
                    statusCounts.merge(result.statusCode, 1L, Long::sum);
                } else {
                    errorCounts.merge(result.error, 1L, Long::sum);
                }
            }

            System.out.printf(Locale.ROOT, "Scenario: %s%n", config.scenario.wireName);
            System.out.printf(Locale.ROOT, "Base URL: %s%n", config.baseUrl);
            System.out.printf(Locale.ROOT, "Requests: %d, concurrency: %d, users: %d, sameUser: %s%n",
                    config.requests, config.concurrency, config.userCount, config.sameUser);
            System.out.printf(Locale.ROOT, "Elapsed: %.2fs, throughput: %.2f req/s%n", elapsedSeconds, throughput);
            System.out.printf(Locale.ROOT, "Success: %d, failed/non-2xx: %d, successRate: %.2f%%%n",
                    success, failed, percentage(success, results.size()));
            System.out.printf(Locale.ROOT, "Latency ms: min=%.2f, avg=%.2f, p50=%.2f, p90=%.2f, p95=%.2f, p99=%.2f, max=%.2f%n",
                    millis(first(latencies)),
                    millis(average(latencies)),
                    millis(percentile(latencies, 0.50)),
                    millis(percentile(latencies, 0.90)),
                    millis(percentile(latencies, 0.95)),
                    millis(percentile(latencies, 0.99)),
                    millis(last(latencies)));
            System.out.println("Status counts: " + statusCounts);
            if (!errorCounts.isEmpty()) {
                System.out.println("Error counts: " + errorCounts);
            }
            observabilityReport.printIfAvailable(config);
        }

        private static double percentage(long value, int total) {
            return total == 0 ? 0.0 : value * 100.0 / total;
        }

        private static long first(List<Long> values) {
            return values.isEmpty() ? 0 : values.get(0);
        }

        private static long last(List<Long> values) {
            return values.isEmpty() ? 0 : values.get(values.size() - 1);
        }

        private static long average(List<Long> values) {
            if (values.isEmpty()) {
                return 0;
            }
            long sum = 0;
            for (long value : values) {
                sum += value;
            }
            return sum / values.size();
        }

        private static long percentile(List<Long> values, double percentile) {
            if (values.isEmpty()) {
                return 0;
            }
            int index = (int) Math.ceil(percentile * values.size()) - 1;
            return values.get(Math.max(0, Math.min(index, values.size() - 1)));
        }

        private static double millis(long nanos) {
            return nanos / 1_000_000.0;
        }
    }

    private static final class ObservabilityProbe {
        private static final Pattern LONG_FIELD_PATTERN = Pattern.compile("\"%s\"\\s*:\\s*(\\d+)");

        private final HttpClient client;
        private final Config config;

        private ObservabilityProbe(HttpClient client, Config config) {
            this.client = client;
            this.config = config;
        }

        static ObservabilityProbe create(HttpClient client, Config config) {
            return new ObservabilityProbe(client, config);
        }

        void beforeBenchmark() {
            if (config.scenario == Scenario.INVENTORY_REFRESH && config.collectAppMetrics) {
                fetchAppMetrics(true);
            }
        }

        KafkaLagSnapshot kafkaLag() {
            if (config.scenario != Scenario.INVENTORY_REFRESH || !config.monitorKafka) {
                return KafkaLagSnapshot.unavailable("disabled");
            }
            try {
                return KafkaLagMonitor.snapshot(
                        config.kafkaBootstrapServers,
                        config.kafkaTopic,
                        config.kafkaGroupId
                );
            } catch (Exception e) {
                return KafkaLagSnapshot.unavailable(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        CompletionSnapshot waitForCompletion(LocalDateTime startedAt, List<String> acceptedRequestIds, int waitSeconds) {
            if (config.scenario != Scenario.INVENTORY_REFRESH || !config.monitorDb) {
                return CompletionSnapshot.unavailable("disabled");
            }
            if (!acceptedRequestIds.isEmpty()) {
                return waitForCompletionByRequestIds(acceptedRequestIds, waitSeconds);
            }
            return CompletionSnapshot.unavailable("no accepted request ids");
        }

        private CompletionSnapshot waitForCompletionByRequestIds(List<String> acceptedRequestIds, int waitSeconds) {
            long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(waitSeconds);
            CompletionSnapshot last = CompletionSnapshot.unavailable("not queried");
            while (System.nanoTime() <= deadlineNanos) {
                last = completionSnapshotByRequestIds(acceptedRequestIds);
                if (last.available() && last.processing() == 0 && last.totalJobs() >= acceptedRequestIds.size()) {
                    return last;
                }
                sleep(1_000);
            }
            return last;
        }

        AppMetricsSnapshot appMetrics() {
            if (config.scenario != Scenario.INVENTORY_REFRESH || !config.collectAppMetrics) {
                return AppMetricsSnapshot.unavailable("disabled");
            }
            return fetchAppMetrics(false);
        }

        private AppMetricsSnapshot fetchAppMetrics(boolean reset) {
            try {
                String path = "/api/benchmark/steam-api-metrics?reset=" + reset;
                HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl + path))
                        .timeout(Duration.ofSeconds(config.requestTimeoutSeconds))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return AppMetricsSnapshot.unavailable("HTTP " + response.statusCode());
                }
                String body = response.body();
                return new AppMetricsSnapshot(
                        true,
                        extractLong(body, "steamApiTotalCalls"),
                        extractLong(body, "steamApiPeakCallsPerSecond"),
                        null
                );
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return AppMetricsSnapshot.unavailable(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        private CompletionSnapshot completionSnapshot(LocalDateTime startedAt, long acceptedRequests) {
            String sql = """
                    SELECT status, COUNT(*)
                    FROM inventory_refresh_job
                    WHERE created_at >= ?
                    GROUP BY status
                    """;
            try (Connection connection = DriverManager.getConnection(config.dbUrl, config.dbUsername, config.dbPassword);
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.valueOf(startedAt));
                long done = 0;
                long failed = 0;
                long processing = 0;
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String status = resultSet.getString(1);
                        long count = resultSet.getLong(2);
                        if ("DONE".equals(status)) {
                            done = count;
                        } else if ("FAILED".equals(status)) {
                            failed = count;
                        } else if ("PROCESSING".equals(status)) {
                            processing = count;
                        }
                    }
                }
                return new CompletionSnapshot(true, acceptedRequests, done, failed, processing, null);
            } catch (Exception e) {
                return CompletionSnapshot.unavailable(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        private CompletionSnapshot completionSnapshotByRequestIds(List<String> acceptedRequestIds) {
            try (Connection connection = DriverManager.getConnection(config.dbUrl, config.dbUsername, config.dbPassword)) {
                long done = 0;
                long failed = 0;
                long processing = 0;
                int chunkSize = 500;
                for (int start = 0; start < acceptedRequestIds.size(); start += chunkSize) {
                    int end = Math.min(start + chunkSize, acceptedRequestIds.size());
                    String placeholders = String.join(",", Collections.nCopies(end - start, "?"));
                    String sql = "SELECT status, COUNT(*) FROM inventory_refresh_job "
                            + "WHERE request_id IN (" + placeholders + ") GROUP BY status";
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        for (int i = start; i < end; i++) {
                            statement.setString(i - start + 1, acceptedRequestIds.get(i));
                        }
                        try (ResultSet resultSet = statement.executeQuery()) {
                            while (resultSet.next()) {
                                String status = resultSet.getString(1);
                                long count = resultSet.getLong(2);
                                if ("DONE".equals(status)) {
                                    done += count;
                                } else if ("FAILED".equals(status)) {
                                    failed += count;
                                } else if ("PROCESSING".equals(status)) {
                                    processing += count;
                                }
                            }
                        }
                    }
                }
                return new CompletionSnapshot(true, acceptedRequestIds.size(), done, failed, processing, null);
            } catch (Exception e) {
                return CompletionSnapshot.unavailable(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        private static long extractLong(String json, String field) {
            Matcher matcher = Pattern.compile(String.format(LONG_FIELD_PATTERN.pattern(), Pattern.quote(field)))
                    .matcher(json);
            if (!matcher.find()) {
                return 0;
            }
            return Long.parseLong(matcher.group(1));
        }

        private static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class KafkaLagMonitor {
        static KafkaLagSnapshot snapshot(String bootstrapServers, String topic, String groupId) throws Exception {
            Properties properties = new Properties();
            properties.put("bootstrap.servers", bootstrapServers);
            properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            properties.put("group.id", "benchmark-lag-reader-" + System.nanoTime());
            properties.put("enable.auto.commit", "false");

            try (AdminClient adminClient = AdminClient.create(Map.of("bootstrap.servers", bootstrapServers));
                 KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
                List<TopicPartition> partitions = consumer.partitionsFor(topic)
                        .stream()
                        .map(info -> new TopicPartition(topic, info.partition()))
                        .toList();
                Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);

                ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(groupId);
                Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> committed =
                        offsetsResult.partitionsToOffsetAndMetadata().get();

                long endOffsetTotal = 0;
                long committedTotal = 0;
                long lag = 0;
                for (TopicPartition partition : partitions) {
                    long endOffset = endOffsets.getOrDefault(partition, 0L);
                    long committedOffset = committed.getOrDefault(partition, null) == null
                            ? 0L
                            : committed.get(partition).offset();
                    endOffsetTotal += endOffset;
                    committedTotal += committedOffset;
                    lag += Math.max(0L, endOffset - committedOffset);
                }
                return new KafkaLagSnapshot(true, endOffsetTotal, committedTotal, lag, null);
            }
        }
    }

    private record ObservabilityReport(
            KafkaLagSnapshot kafkaLagBefore,
            KafkaLagSnapshot kafkaLagAfterSubmit,
            CompletionSnapshot completion,
            AppMetricsSnapshot appMetrics
    ) {
        void printIfAvailable(Config config) {
            if (config.scenario != Scenario.INVENTORY_REFRESH) {
                return;
            }
            System.out.println("Inventory async metrics:");
            if (appMetrics.available()) {
                System.out.printf(Locale.ROOT,
                        "  Steam API calls: total=%d, peak=%d calls/s%n",
                        appMetrics.totalCalls(),
                        appMetrics.peakCallsPerSecond());
            } else {
                System.out.println("  Steam API calls: unavailable (" + appMetrics.error() + ")");
            }
            if (kafkaLagBefore.available() && kafkaLagAfterSubmit.available()) {
                System.out.printf(Locale.ROOT,
                        "  Kafka lag: before=%d, afterSubmit=%d, endOffsetAfter=%d, committedAfter=%d%n",
                        kafkaLagBefore.lag(),
                        kafkaLagAfterSubmit.lag(),
                        kafkaLagAfterSubmit.endOffset(),
                        kafkaLagAfterSubmit.committedOffset());
            } else {
                String error = !kafkaLagAfterSubmit.available() ? kafkaLagAfterSubmit.error() : kafkaLagBefore.error();
                System.out.println("  Kafka lag: unavailable (" + error + ")");
            }
            if (completion.available()) {
                long totalJobs = completion.totalJobs();
                System.out.printf(Locale.ROOT,
                        "  Job completion: acceptedHttp=%d, trackedJobs=%d, done=%d, failed=%d, processing=%d, finalRate=%.2f%%%n",
                        completion.acceptedRequests(),
                        totalJobs,
                        completion.done(),
                        completion.failed(),
                        completion.processing(),
                        totalJobs == 0 ? 0.0 : completion.done() * 100.0 / totalJobs);
            } else {
                System.out.println("  Job completion: unavailable (" + completion.error() + ")");
            }
        }
    }

    private record KafkaLagSnapshot(
            boolean available,
            long endOffset,
            long committedOffset,
            long lag,
            String error
    ) {
        static KafkaLagSnapshot unavailable(String error) {
            return new KafkaLagSnapshot(false, 0, 0, 0, error);
        }
    }

    private record CompletionSnapshot(
            boolean available,
            long acceptedRequests,
            long done,
            long failed,
            long processing,
            String error
    ) {
        static CompletionSnapshot unavailable(String error) {
            return new CompletionSnapshot(false, 0, 0, 0, 0, error);
        }

        long totalJobs() {
            return done + failed + processing;
        }
    }

    private record AppMetricsSnapshot(
            boolean available,
            long totalCalls,
            long peakCallsPerSecond,
            String error
    ) {
        static AppMetricsSnapshot unavailable(String error) {
            return new AppMetricsSnapshot(false, 0, 0, error);
        }
    }

    private enum Scenario {
        USER_INFO("user-info"),
        INVENTORY_READ("inventory-read"),
        INVENTORY_REFRESH("inventory-refresh"),
        CHAT("chat"),
        ALL("all");

        private final String wireName;

        Scenario(String wireName) {
            this.wireName = wireName;
        }

        static Scenario from(String raw) {
            String normalized = Objects.requireNonNullElse(raw, "").toLowerCase(Locale.ROOT);
            for (Scenario scenario : values()) {
                if (scenario.wireName.equals(normalized)) {
                    return scenario;
                }
            }
            throw new IllegalArgumentException("Unsupported scenario: " + raw);
        }
    }

    private record Config(
            Scenario scenario,
            String baseUrl,
            int requests,
            int warmupRequests,
            int concurrency,
            int userCount,
            boolean sameUser,
            boolean randomUsers,
            String steamIdsCsv,
            String steamIdsFile,
            boolean allowReuseSteamIds,
            String message,
            boolean followUp,
            boolean uniqueInventoryUsers,
            boolean collectAppMetrics,
            boolean monitorKafka,
            String kafkaBootstrapServers,
            String kafkaTopic,
            String kafkaGroupId,
            boolean monitorDb,
            String dbUrl,
            String dbUsername,
            String dbPassword,
            int completionWaitSeconds,
            String benchmarkRunId,
            int connectTimeoutSeconds,
            int requestTimeoutSeconds,
            boolean help
    ) {
        static Config parse(String[] args) {
            Map<String, String> options = new HashMap<>();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    options.put("help", "true");
                    continue;
                }
                if (!arg.startsWith("--")) {
                    throw new IllegalArgumentException("Unexpected argument: " + arg);
                }
                String key = arg.substring(2);
                String value = "true";
                int equalsIndex = key.indexOf('=');
                if (equalsIndex >= 0) {
                    value = key.substring(equalsIndex + 1);
                    key = key.substring(0, equalsIndex);
                } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    value = args[++i];
                }
                options.put(key, value);
            }

            ConfigFileValues fileValues = ConfigFileValues.load();

            return new Config(
                    Scenario.from(options.getOrDefault("scenario", "all")),
                    trimTrailingSlash(options.getOrDefault("base-url", "http://localhost:3000")),
                    positiveInt(options, "requests", 1000),
                    nonNegativeInt(options, "warmup", 50),
                    positiveInt(options, "concurrency", 50),
                    positiveInt(options, "users", 100),
                    bool(options, "same-user", false),
                    bool(options, "random-users", false),
                    options.getOrDefault("steam-ids", ""),
                    options.getOrDefault("steam-ids-file", ""),
                    bool(options, "allow-reuse-steam-ids", false),
                    options.getOrDefault("message", "Analyze AK-47 Redline price trend"),
                    bool(options, "follow-up", false),
                    bool(options, "unique-inventory-users", true),
                    bool(options, "collect-app-metrics", true),
                    bool(options, "monitor-kafka", true),
                    optionOrConfig(options, "kafka-bootstrap-servers", fileValues, "spring.kafka.bootstrap-servers", "KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
                    options.getOrDefault("kafka-topic", "inventory-refresh-topic"),
                    optionOrConfig(options, "kafka-group-id", fileValues, "spring.kafka.consumer.group-id", "KAFKA_CONSUMER_GROUP_ID", "inventory-refresh-group"),
                    bool(options, "monitor-db", true),
                    optionOrConfig(options, "db-url", fileValues, "spring.datasource.url", "DB_URL", "jdbc:mysql://localhost:3306/price_prediction?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=utf-8"),
                    optionOrConfig(options, "db-username", fileValues, "spring.datasource.username", "DB_USERNAME", "root"),
                    optionOrConfig(options, "db-password", fileValues, "spring.datasource.password", "DB_PASSWORD", ""),
                    nonNegativeInt(options, "completion-wait", 60),
                    options.getOrDefault("run-id", defaultRunId()),
                    positiveInt(options, "connect-timeout", 5),
                    positiveInt(options, "request-timeout", 60),
                    bool(options, "help", false)
            );
        }

        Config withScenario(Scenario nextScenario) {
            return new Config(
                    nextScenario,
                    baseUrl,
                    requests,
                    warmupRequests,
                    concurrency,
                    userCount,
                    sameUser,
                    randomUsers,
                    steamIdsCsv,
                    steamIdsFile,
                    allowReuseSteamIds,
                    message,
                    followUp,
                    uniqueInventoryUsers,
                    collectAppMetrics,
                    monitorKafka,
                    kafkaBootstrapServers,
                    kafkaTopic,
                    kafkaGroupId,
                    monitorDb,
                    dbUrl,
                    dbUsername,
                    dbPassword,
                    completionWaitSeconds,
                    benchmarkRunId,
                    connectTimeoutSeconds,
                    requestTimeoutSeconds,
                    help
            );
        }

        Config withRequests(int nextRequests) {
            return new Config(
                    scenario,
                    baseUrl,
                    nextRequests,
                    0,
                    concurrency,
                    userCount,
                    sameUser,
                    randomUsers,
                    steamIdsCsv,
                    steamIdsFile,
                    allowReuseSteamIds,
                    message,
                    followUp,
                    uniqueInventoryUsers,
                    collectAppMetrics,
                    monitorKafka,
                    kafkaBootstrapServers,
                    kafkaTopic,
                    kafkaGroupId,
                    monitorDb,
                    dbUrl,
                    dbUsername,
                    dbPassword,
                    completionWaitSeconds,
                    benchmarkRunId,
                    connectTimeoutSeconds,
                    requestTimeoutSeconds,
                    help
            );
        }

        List<String> steamIds() {
            List<String> ids;
            if (steamIdsFile != null && !steamIdsFile.isBlank()) {
                ids = readSteamIdsFile(Path.of(steamIdsFile));
            } else if (steamIdsCsv != null && !steamIdsCsv.isBlank()) {
                ids = parseSteamIds(steamIdsCsv);
            } else {
                int generatedUserCount = userCount;
                if (scenario == Scenario.INVENTORY_REFRESH && uniqueInventoryUsers && !sameUser && !randomUsers) {
                    generatedUserCount = Math.max(userCount, requests + warmupRequests);
                }

                List<String> generatedIds = new ArrayList<>(generatedUserCount);
                long base = 76561198000000000L;
                for (int i = 0; i < generatedUserCount; i++) {
                    generatedIds.add(Long.toString(base + i));
                }
                ids = generatedIds;
            }

            validateSteamIds(ids);
            return Collections.unmodifiableList(ids);
        }

        private void validateSteamIds(List<String> ids) {
            if (ids.isEmpty()) {
                throw new IllegalArgumentException("No Steam IDs are available");
            }
            if (scenario != Scenario.INVENTORY_REFRESH || sameUser || randomUsers || allowReuseSteamIds) {
                return;
            }
            int required = requests + warmupRequests;
            if (ids.size() < required) {
                throw new IllegalArgumentException(
                        "inventory-refresh needs at least " + required + " unique Steam IDs when warmup="
                                + warmupRequests + " and requests=" + requests
                                + ". Provided " + ids.size()
                                + ". Add more IDs, lower --requests/--warmup, or set --allow-reuse-steam-ids true."
                );
            }
        }

        private static List<String> readSteamIdsFile(Path path) {
            try {
                return parseSteamIds(Files.readString(path, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read --steam-ids-file " + path + ": " + e.getMessage(), e);
            }
        }

        private static List<String> parseSteamIds(String raw) {
            return Arrays.stream(raw.split("[,\\s]+"))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
        }

        String messageFor(int requestNo) {
            return message + " #" + requestNo;
        }

        String requestId(String phase, int requestNo) {
            return benchmarkRunId + ":" + phase + ":" + requestNo;
        }

        static void printHelp() {
            System.out.println("""
                    HighFrequencyLoadBenchmark options:
                      --scenario <all|user-info|inventory-read|inventory-refresh|chat>
                      --base-url <url>              default: http://localhost:3000
                      --requests <n>                default: 1000
                      --warmup <n>                  default: 50
                      --concurrency <n>             default: 50
                      --users <n>                   default: 100
                      --same-user <true|false>      stress one user's Redis lock/rate-limit path
                      --random-users <true|false>   choose users randomly instead of round-robin
                      --unique-inventory-users <true|false>
                                                   default true for inventory-refresh; avoids repeat-user rate-limit tests
                      --steam-ids <id1,id2>         explicit Steam IDs, overrides --users
                      --steam-ids-file <path>       file with real Steam IDs; comma, whitespace, or newline separated
                      --allow-reuse-steam-ids <true|false>
                                                   default false; fail fast if inventory-refresh would reuse IDs
                      --message <text>              chat prompt for --scenario chat
                      --follow-up <true|false>      chat followUp flag
                      --collect-app-metrics <true|false>
                                                   read Steam API counters from /api/benchmark/steam-api-metrics
                      --monitor-kafka <true|false>  collect Kafka consumer-group lag
                      --kafka-bootstrap-servers <servers>
                      --kafka-topic <topic>         default: inventory-refresh-topic
                      --kafka-group-id <group>      default: inventory-refresh-group
                      --monitor-db <true|false>     collect inventory_refresh_job completion rate
                      --db-url <jdbc-url>
                      --db-username <username>
                      --db-password <password>
                      --completion-wait <seconds>   wait for async job completion metrics, default: 60
                      --run-id <id>                  benchmark requestId prefix, auto-generated by default
                      --connect-timeout <seconds>   default: 5
                      --request-timeout <seconds>   default: 60
                    """);
        }

        private static String trimTrailingSlash(String value) {
            String trimmed = value.trim();
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed;
        }

        private static int positiveInt(Map<String, String> options, String key, int defaultValue) {
            int value = Integer.parseInt(options.getOrDefault(key, Integer.toString(defaultValue)));
            if (value <= 0) {
                throw new IllegalArgumentException("--" + key + " must be positive");
            }
            return value;
        }

        private static int nonNegativeInt(Map<String, String> options, String key, int defaultValue) {
            int value = Integer.parseInt(options.getOrDefault(key, Integer.toString(defaultValue)));
            if (value < 0) {
                throw new IllegalArgumentException("--" + key + " must be non-negative");
            }
            return value;
        }

        private static boolean bool(Map<String, String> options, String key, boolean defaultValue) {
            return Boolean.parseBoolean(options.getOrDefault(key, Boolean.toString(defaultValue)));
        }

        private static String defaultRunId() {
            return "bench" + Long.toString(System.currentTimeMillis(), 36);
        }

        private static String optionOrConfig(
                Map<String, String> options,
                String optionKey,
                ConfigFileValues fileValues,
                String configKey,
                String envKey,
                String defaultValue
        ) {
            if (options.containsKey(optionKey)) {
                return options.get(optionKey);
            }
            return fileValues.get(configKey, envKey, defaultValue);
        }

        private static String env(String key, String defaultValue) {
            String value = System.getenv(key);
            return value == null || value.isBlank() ? defaultValue : value;
        }
    }

    private static final class ConfigFileValues {
        private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

        private final Map<String, String> yamlValues;
        private final Map<String, String> dotenvValues;

        private ConfigFileValues(Map<String, String> yamlValues, Map<String, String> dotenvValues) {
            this.yamlValues = yamlValues;
            this.dotenvValues = dotenvValues;
        }

        static ConfigFileValues load() {
            Map<String, String> dotenv = loadDotenv(Path.of(".env"));
            Map<String, String> yaml = loadSimpleYaml(Path.of("src", "test", "resources", "application-test.yml"));
            return new ConfigFileValues(yaml, dotenv);
        }

        String get(String configKey, String envKey, String defaultValue) {
            String raw = yamlValues.get(configKey);
            if (raw == null || raw.isBlank()) {
                return Config.env(envKey, defaultValue);
            }
            return resolvePlaceholders(raw, defaultValue);
        }

        private String resolvePlaceholders(String raw, String defaultValue) {
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(raw);
            StringBuffer resolved = new StringBuffer();
            while (matcher.find()) {
                String key = matcher.group(1);
                String placeholderDefault = matcher.group(2);
                String replacement = System.getenv(key);
                if (replacement == null) {
                    replacement = dotenvValues.get(key);
                }
                if (replacement == null) {
                    replacement = placeholderDefault;
                }
                if (replacement == null) {
                    replacement = defaultValue;
                }
                matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement == null ? "" : replacement));
            }
            matcher.appendTail(resolved);
            String value = stripQuotes(resolved.toString().trim());
            return value.isBlank() ? defaultValue : value;
        }

        private static Map<String, String> loadDotenv(Path path) {
            Map<String, String> values = new HashMap<>();
            if (!Files.isRegularFile(path)) {
                return values;
            }
            try {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.isBlank() || trimmed.startsWith("#")) {
                        continue;
                    }
                    if (trimmed.startsWith("export ")) {
                        trimmed = trimmed.substring("export ".length()).trim();
                    }
                    int equalsIndex = trimmed.indexOf('=');
                    if (equalsIndex <= 0) {
                        continue;
                    }
                    String key = trimmed.substring(0, equalsIndex).trim();
                    String value = stripQuotes(trimmed.substring(equalsIndex + 1).trim());
                    values.put(key, value);
                }
            } catch (IOException e) {
                System.out.println("Could not read .env: " + e.getMessage());
            }
            return values;
        }

        private static Map<String, String> loadSimpleYaml(Path path) {
            Map<String, String> values = new HashMap<>();
            if (!Files.isRegularFile(path)) {
                return values;
            }
            List<String> keyStack = new ArrayList<>();
            try {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    String withoutComment = stripYamlComment(line);
                    if (withoutComment.trim().isBlank()) {
                        continue;
                    }
                    int indent = countLeadingSpaces(withoutComment);
                    int level = indent / 2;
                    String trimmed = withoutComment.trim();
                    int colonIndex = trimmed.indexOf(':');
                    if (colonIndex <= 0) {
                        continue;
                    }
                    String key = trimmed.substring(0, colonIndex).trim();
                    String value = trimmed.substring(colonIndex + 1).trim();
                    while (keyStack.size() > level) {
                        keyStack.remove(keyStack.size() - 1);
                    }
                    if (value.isBlank()) {
                        while (keyStack.size() > level) {
                            keyStack.remove(keyStack.size() - 1);
                        }
                        keyStack.add(key);
                    } else {
                        List<String> fullKey = new ArrayList<>(keyStack);
                        fullKey.add(key);
                        values.put(String.join(".", fullKey), stripQuotes(value));
                    }
                }
            } catch (IOException e) {
                System.out.println("Could not read application-test.yml: " + e.getMessage());
            }
            return values;
        }

        private static int countLeadingSpaces(String line) {
            int count = 0;
            while (count < line.length() && line.charAt(count) == ' ') {
                count++;
            }
            return count;
        }

        private static String stripYamlComment(String line) {
            boolean singleQuoted = false;
            boolean doubleQuoted = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '\'' && !doubleQuoted) {
                    singleQuoted = !singleQuoted;
                } else if (c == '"' && !singleQuoted) {
                    doubleQuoted = !doubleQuoted;
                } else if (c == '#' && !singleQuoted && !doubleQuoted) {
                    return line.substring(0, i);
                }
            }
            return line;
        }

        private static String stripQuotes(String value) {
            if (value.length() >= 2) {
                char first = value.charAt(0);
                char last = value.charAt(value.length() - 1);
                if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                    return value.substring(1, value.length() - 1);
                }
            }
            return value;
        }
    }

    private static String url(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }

    private static String json(String raw) {
        StringBuilder builder = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }
}
