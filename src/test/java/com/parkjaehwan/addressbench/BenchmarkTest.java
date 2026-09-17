package com.parkjaehwan.addressbench;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BenchmarkTest {
    @Test
    void percentileMatchesCeilDefinition() {
        double[] values = {5, 1, 4, 2, 3};
        assertEquals(3, Benchmark.percentile(values, 50));
        assertEquals(5, Benchmark.percentile(values, 95));
        assertEquals(1, Benchmark.percentile(values, 0));
    }

    @Test
    void exhaustiveLcsRanksExactAliasFirst() throws Exception {
        ExhaustiveLcsMatcher matcher = new ExhaustiveLcsMatcher(
                new int[]{0, 1, 2},
                new String[]{"서울 강남구 테헤란로 152", "부산 해운대구 해운대로 10", "서울 강남구 테헤란로 12"});
        Matcher.SearchResult result = matcher.search("서울강남구테헤란노152");
        assertEquals(0, result.matches().get(0).addressId());
        assertEquals(3, result.matches().size());

        Benchmark.Metrics metrics = Benchmark.run(matcher, List.of(new Benchmark.Query("서울강남구테헤란노152", 0),
                new Benchmark.Query("해운대로10", 1)), 0);
        assertEquals(2, metrics.queries());
        assertEquals(1.0, metrics.top1_accuracy());
        assertEquals(1.0, metrics.mrr());
    }
}
