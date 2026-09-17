package com.parkjaehwan.addressbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratorTest {
    @Test
    void generatorIsReproducible(@TempDir Path tmp) throws Exception {
        Path first = tmp.resolve("first");
        Path second = tmp.resolve("second");
        Generator.generate(100, 10, first, 42);
        Generator.generate(100, 10, second, 42);
        assertArrayEquals(Files.readAllBytes(first.resolve("addresses.ndjson")), Files.readAllBytes(second.resolve("addresses.ndjson")));
        assertArrayEquals(Files.readAllBytes(first.resolve("queries.ndjson")), Files.readAllBytes(second.resolve("queries.ndjson")));
        assertEquals(100, new ObjectMapper().readTree(first.resolve("metadata.json").toFile()).get("size").asInt());
        assertEquals(10, Files.readAllLines(first.resolve("queries.ndjson")).size());
    }

    @Test
    void canonicalAddressesAreUniqueAtScale() {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            values.add(Generator.canonicalAddress(i));
        }
        assertEquals(100_000, values.size());
    }
}
