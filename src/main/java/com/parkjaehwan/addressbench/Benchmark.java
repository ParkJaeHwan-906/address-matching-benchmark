package com.parkjaehwan.addressbench;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 정확도(Top-1·Top-5·MRR)와 지연시간(p50·p95·p99)·처리량(QPS)을 잰다.
 */
public final class Benchmark {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Benchmark() {
    }

    @JsonPropertyOrder({"queries", "top1_accuracy", "top5_accuracy", "mrr",
            "latency_ms_p50", "latency_ms_p95", "latency_ms_p99", "throughput_qps"})
    public record Metrics(int queries, double top1_accuracy, double top5_accuracy, double mrr,
                          double latency_ms_p50, double latency_ms_p95, double latency_ms_p99,
                          double throughput_qps) {
    }

    public record Query(String query, int expectedId) {
    }

    /** Python 기준선과 같은 정의: 정렬 후 ceil(p/100·n)-1 번째 값. */
    public static double percentile(double[] values, double percent) {
        double[] ordered = values.clone();
        Arrays.sort(ordered);
        int position = (int) Math.ceil(percent / 100.0 * ordered.length) - 1;
        return ordered[Math.max(0, position)];
    }

    public static Metrics run(Matcher matcher, List<Query> queries, int warmup) throws Exception {
        if (queries.isEmpty()) {
            throw new IllegalArgumentException("query set must not be empty");
        }
        for (Query item : queries.subList(0, Math.min(warmup, queries.size()))) {
            matcher.search(item.query());
        }
        double[] latencies = new double[queries.size()];
        int top1 = 0;
        int top5 = 0;
        double reciprocalSum = 0;
        long wallStarted = System.nanoTime();
        for (int i = 0; i < queries.size(); i++) {
            Query item = queries.get(i);
            Matcher.SearchResult result = matcher.search(item.query());
            latencies[i] = result.elapsedNanos() / 1_000_000.0;
            int rank = -1;
            List<Match> matches = result.matches();
            for (int r = 0; r < matches.size(); r++) {
                if (matches.get(r).addressId() == item.expectedId()) {
                    rank = r;
                    break;
                }
            }
            if (rank == 0) {
                top1++;
            }
            if (rank >= 0) {
                top5++;
                reciprocalSum += 1.0 / (rank + 1);
            }
        }
        double wallSeconds = (System.nanoTime() - wallStarted) / 1_000_000_000.0;
        int count = queries.size();
        return new Metrics(count, (double) top1 / count, (double) top5 / count, reciprocalSum / count,
                percentile(latencies, 50), percentile(latencies, 95), percentile(latencies, 99), count / wallSeconds);
    }

    public static List<Query> loadQueries(Path path, Integer limit) throws IOException {
        List<Query> result = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = MAPPER.readTree(line);
                result.add(new Query(node.get("query").asText(), node.get("expected_id").asInt()));
                if (limit != null && result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    /** addresses.ndjson 을 (id, alias) 배열로 읽는다. 1천만 건도 배열 두 개로만 든다. */
    public static ExhaustiveLcsMatcher loadLcsMatcher(Path addresses) throws IOException {
        List<String> aliasList = new ArrayList<>();
        List<Integer> idList = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(addresses, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = MAPPER.readTree(line);
                idList.add(node.get("id").asInt());
                aliasList.add(node.get("alias").asText());
            }
        }
        int[] ids = new int[idList.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = idList.get(i);
        }
        return new ExhaustiveLcsMatcher(ids, aliasList.toArray(new String[0]));
    }

    public static void writeReport(Path path, Path dataset, Map<String, Metrics> results) throws IOException {
        JsonNode metadata = MAPPER.readTree(Files.readString(dataset.resolve("metadata.json"), StandardCharsets.UTF_8));
        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("java", System.getProperty("java.version") + " (" + System.getProperty("java.vm.name") + ")");
        environment.put("platform", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("dataset", metadata);
        report.put("environment", environment);
        report.put("results", results);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n", StandardCharsets.UTF_8);
    }
}
