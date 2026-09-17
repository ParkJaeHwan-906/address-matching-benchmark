package com.parkjaehwan.addressbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 2~4-gram 역색인 검색. 외부 클라이언트 없이 java.net.http만 쓴다.
 */
public final class ElasticsearchMatcher implements Matcher {
    /** 색인 설정: 문자·숫자만 남기는 char_filter → 2~4-gram 토크나이저 → lowercase. */
    static final String INDEX_SETTINGS = """
            {
              "settings": {
                "index": {"max_ngram_diff": 2},
                "analysis": {
                  "char_filter": {
                    "address_cleanup": {"type": "pattern_replace", "pattern": "[^\\\\p{L}\\\\p{N}]", "replacement": ""}
                  },
                  "tokenizer": {
                    "address_ngram": {"type": "ngram", "min_gram": 2, "max_gram": 4, "token_chars": ["letter", "digit"]}
                  },
                  "analyzer": {
                    "address_ngram_analyzer": {
                      "type": "custom",
                      "char_filter": ["address_cleanup"],
                      "tokenizer": "address_ngram",
                      "filter": ["lowercase"]
                    }
                  }
                }
              },
              "mappings": {
                "properties": {
                  "id": {"type": "long"},
                  "canonical": {"type": "keyword"},
                  "alias": {"type": "text", "analyzer": "address_ngram_analyzer", "search_analyzer": "address_ngram_analyzer"}
                }
              }
            }
            """;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final String baseUrl;
    private final String index;
    private final HttpClient client;

    public ElasticsearchMatcher(String baseUrl, String index) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.index = index;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public void recreateIndex() throws IOException, InterruptedException {
        HttpResponse<String> deleted = send("DELETE", "/" + index, null, "application/json");
        if (deleted.statusCode() != 200 && deleted.statusCode() != 404) {
            throw new IOException("DELETE " + index + " failed: " + deleted.statusCode() + " " + deleted.body());
        }
        HttpResponse<String> created = send("PUT", "/" + index, INDEX_SETTINGS, "application/json");
        ensureOk(created, "PUT " + index);
    }

    public void bulkIndex(Path dataset) throws IOException, InterruptedException {
        bulkIndex(dataset, 5000);
    }

    public void bulkIndex(Path dataset, int batchSize) throws IOException, InterruptedException {
        StringBuilder batch = new StringBuilder(batchSize * 256);
        int pending = 0;
        long total = 0;
        try (BufferedReader reader = Files.newBufferedReader(dataset, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode record = MAPPER.readTree(line);
                batch.append("{\"index\":{\"_index\":\"").append(index).append("\",\"_id\":")
                        .append(record.get("id").asLong()).append("}}\n");
                batch.append(line).append('\n');
                pending++;
                if (pending >= batchSize) {
                    bulk(batch.toString());
                    total += pending;
                    batch.setLength(0);
                    pending = 0;
                    if (total % 500_000 == 0) {
                        System.err.printf("indexed %,d%n", total);
                    }
                }
            }
        }
        if (pending > 0) {
            bulk(batch.toString());
            total += pending;
        }
        ensureOk(send("POST", "/" + index + "/_refresh", null, "application/json"), "refresh");
        System.err.printf("indexed %,d documents into %s%n", total, index);
    }

    private void bulk(String body) throws IOException, InterruptedException {
        HttpResponse<String> response = send("POST", "/_bulk", body, "application/x-ndjson");
        ensureOk(response, "bulk");
        JsonNode result = MAPPER.readTree(response.body());
        if (result.path("errors").asBoolean(false)) {
            throw new IOException("Elasticsearch bulk indexing returned errors");
        }
    }

    @Override
    public SearchResult search(String query, int topK) throws IOException, InterruptedException {
        String body = MAPPER.writeValueAsString(MAPPER.createObjectNode()
                .put("size", topK)
                .<com.fasterxml.jackson.databind.node.ObjectNode>set("_source", MAPPER.createArrayNode().add("id").add("alias"))
                .set("query", MAPPER.createObjectNode().set("match", MAPPER.createObjectNode()
                        .set("alias", MAPPER.createObjectNode().put("query", query).put("minimum_should_match", "55%")))));
        long started = System.nanoTime();
        HttpResponse<String> response = send("POST", "/" + index + "/_search", body, "application/json");
        long elapsed = System.nanoTime() - started;
        ensureOk(response, "search");
        JsonNode hits = MAPPER.readTree(response.body()).path("hits").path("hits");
        List<Match> matches = new ArrayList<>(hits.size());
        for (JsonNode hit : hits) {
            JsonNode source = hit.get("_source");
            matches.add(new Match(source.get("id").asInt(), source.get("alias").asText(), hit.get("_score").asDouble()));
        }
        return new SearchResult(matches, elapsed);
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Content-Type", contentType)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void ensureOk(HttpResponse<String> response, String what) throws IOException {
        if (response.statusCode() / 100 != 2) {
            throw new IOException(what + " failed: HTTP " + response.statusCode() + " " + response.body());
        }
    }
}
