package com.parkjaehwan.addressbench;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * address-bench CLI — generate · index · compare.
 */
@Command(name = "address-bench", mixinStandardHelpOptions = true, version = "0.2.0",
        description = "LCS vs Elasticsearch n-gram address matching benchmark",
        subcommands = {Cli.Generate.class, Cli.Index.class, Cli.Compare.class})
public final class Cli implements Callable<Integer> {

    static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    static ElasticsearchMatcher elasticsearch() {
        return new ElasticsearchMatcher(env("ELASTICSEARCH_URL", "http://localhost:9200"),
                env("ELASTICSEARCH_INDEX", "address-mapping"));
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    @Command(name = "generate", description = "합성 주소·질의 데이터를 만든다")
    static final class Generate implements Callable<Integer> {
        @Option(names = "--size", required = true, description = "매핑 테이블 행 수")
        int size;
        @Option(names = "--queries", defaultValue = "1000", description = "질의 수 (기본 1000)")
        int queries;
        @Option(names = "--output", required = true, description = "출력 폴더")
        Path output;
        @Option(names = "--seed", description = "난수 seed (기본 BENCHMARK_SEED 또는 20260917)")
        Long seed;

        @Override
        public Integer call() throws Exception {
            long effectiveSeed = seed != null ? seed : Long.parseLong(env("BENCHMARK_SEED", "20260917"));
            long started = System.nanoTime();
            Generator.generate(size, queries, output, effectiveSeed);
            System.err.printf("generated %,d addresses → %s (%.1fs)%n", size, output, (System.nanoTime() - started) / 1e9);
            return 0;
        }
    }

    @Command(name = "index", description = "addresses.ndjson 을 Elasticsearch에 색인한다")
    static final class Index implements Callable<Integer> {
        @Option(names = "--dataset", required = true, description = "addresses.ndjson 경로")
        Path dataset;
        @Option(names = "--recreate", description = "인덱스를 지우고 다시 만든다")
        boolean recreate;

        @Override
        public Integer call() throws Exception {
            ElasticsearchMatcher es = elasticsearch();
            if (recreate) {
                es.recreateIndex();
            }
            long started = System.nanoTime();
            es.bulkIndex(dataset);
            System.err.printf("indexing took %.1fs%n", (System.nanoTime() - started) / 1e9);
            return 0;
        }
    }

    @Command(name = "compare", description = "같은 질의로 Elasticsearch와 LCS를 비교한다")
    static final class Compare implements Callable<Integer> {
        @Option(names = "--dataset", required = true, description = "generate 출력 폴더")
        Path dataset;
        @Option(names = "--output", required = true, description = "결과 JSON 경로")
        Path output;
        @Option(names = "--query-limit", description = "앞에서부터 N개 질의만 사용")
        Integer queryLimit;
        @Option(names = "--warmup", defaultValue = "10", description = "측정 전 warm-up 질의 수 (기본 10)")
        int warmup;
        @Option(names = "--skip-lcs", description = "LCS 전수 탐색을 건너뛴다")
        boolean skipLcs;

        @Override
        public Integer call() throws Exception {
            List<Benchmark.Query> queries = Benchmark.loadQueries(dataset.resolve("queries.ndjson"), queryLimit);
            Map<String, Benchmark.Metrics> results = new LinkedHashMap<>();
            System.err.printf("elasticsearch: %d queries%n", queries.size());
            results.put("elasticsearch_ngram", Benchmark.run(elasticsearch(), queries, warmup));
            print("elasticsearch_ngram", results.get("elasticsearch_ngram"));
            if (!skipLcs) {
                long loadStarted = System.nanoTime();
                ExhaustiveLcsMatcher lcs = Benchmark.loadLcsMatcher(dataset.resolve("addresses.ndjson"));
                System.err.printf("lcs: loaded %,d aliases in %.1fs, %d queries%n", lcs.size(), (System.nanoTime() - loadStarted) / 1e9, queries.size());
                results.put("exhaustive_lcs", Benchmark.run(lcs, queries, warmup));
                print("exhaustive_lcs", results.get("exhaustive_lcs"));
            }
            Benchmark.writeReport(output, dataset, results);
            System.err.println("wrote " + output);
            return 0;
        }

        private static void print(String name, Benchmark.Metrics m) {
            System.err.printf("%-20s top1=%.1f%% top5=%.1f%% mrr=%.4f p50=%.2fms p95=%.2fms p99=%.2fms qps=%.2f%n",
                    name, m.top1_accuracy() * 100, m.top5_accuracy() * 100, m.mrr(),
                    m.latency_ms_p50(), m.latency_ms_p95(), m.latency_ms_p99(), m.throughput_qps());
        }
    }

    public static void main(String[] args) {
        int code = new CommandLine(new Cli()).execute(args);
        System.exit(code);
    }
}
