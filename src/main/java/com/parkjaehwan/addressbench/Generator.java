package com.parkjaehwan.addressbench;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 개인정보 없는 합성 주소 생성기. 같은 seed면 같은 파일을 만든다.
 */
public final class Generator {
    static final String[] SIDOS = {"서울특별시", "부산광역시", "대구광역시", "인천광역시", "광주광역시", "대전광역시", "울산광역시", "경기도",
            "강원특별자치도", "충청북도", "충청남도", "전북특별자치도", "전라남도", "경상북도", "경상남도", "제주특별자치도"};
    static final String[] DISTRICTS = {"중구", "서구", "동구", "남구", "북구", "강남구", "송파구", "분당구", "수지구", "일산동구", "해운대구", "유성구"};
    static final String[] ROADS = {"중앙로", "테헤란로", "한밭대로", "충무로", "세종대로", "올림픽로", "송파대로", "판교역로", "광교중앙로", "해운대로"};
    static final String[] BUILDINGS = {"행복아파트", "푸른마을", "센트럴빌", "한빛타워", "미래빌라", "더샵", "아이파크", "래미안", "힐스테이트", "자이"};

    private static final String[][] ABBREVIATIONS = {
            {"특별자치도", "도"}, {"특별시", ""}, {"광역시", ""}, {"아파트", "APT"}, {"빌라", "BILA"}, {" ", ""}};
    private static final String SUBSTITUTES = "가나다라마바사아자0123456789";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Generator() {
    }

    /**
     * 행마다 지번·동 조합이 고유하도록 만든다. 이 불변식이 없으면 서로 다른 ID가 같은 주소 문자열을
     * 가질 수 있어 Top-k 정확도가 검색 품질이 아니라 데이터 충돌을 재게 된다.
     */
    public static String canonicalAddress(int index) {
        int lot = index / 120 + 1;
        int building = index % 120 + 1;
        return SIDOS[index % SIDOS.length] + " "
                + DISTRICTS[(index / 16) % DISTRICTS.length] + " "
                + ROADS[(index / 192) % ROADS.length] + " "
                + lot + "-" + building + " "
                + BUILDINGS[(index / 1920) % BUILDINGS.length] + " "
                + building + "동";
    }

    /** OCR·약칭 오류 규칙: 행정구역 축약, APT 표기, 공백 제거, 한 글자 삭제, 한 글자 치환. */
    public static String noisyAlias(String address, Random rng) {
        String result = address;
        for (String[] pair : ABBREVIATIONS) {
            if (rng.nextDouble() < 0.22) {
                result = result.replace(pair[0], pair[1]);
            }
        }
        if (result.length() > 5 && rng.nextDouble() < 0.55) {
            int position = rng.nextInt(result.length());
            result = result.substring(0, position) + result.substring(position + 1);
        }
        if (result.length() > 5 && rng.nextDouble() < 0.35) {
            int position = rng.nextInt(result.length());
            char replacement = SUBSTITUTES.charAt(rng.nextInt(SUBSTITUTES.length()));
            result = result.substring(0, position) + replacement + result.substring(position + 1);
        }
        return result;
    }

    public static void generate(int size, int queryCount, Path output, long seed) throws IOException {
        Files.createDirectories(output);
        Random rng = new Random(seed);
        Set<Integer> queryIds = sampleIds(size, Math.min(queryCount, size), rng);

        try (BufferedWriter addresses = Files.newBufferedWriter(output.resolve("addresses.ndjson"), StandardCharsets.UTF_8);
             BufferedWriter queries = Files.newBufferedWriter(output.resolve("queries.ndjson"), StandardCharsets.UTF_8)) {
            for (int index = 0; index < size; index++) {
                String canonical = canonicalAddress(index);
                String alias = noisyAlias(canonical, rng);
                addresses.write(MAPPER.writeValueAsString(new AddressRecord(index, canonical, alias)));
                addresses.write('\n');
                if (queryIds.contains(index)) {
                    queries.write(MAPPER.writeValueAsString(new QueryRecord(noisyAlias(alias, rng), index)));
                    queries.write('\n');
                }
            }
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("size", size);
        metadata.put("query_count", queryIds.size());
        metadata.put("seed", seed);
        metadata.put("synthetic", true);
        Files.writeString(output.resolve("metadata.json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(metadata) + "\n", StandardCharsets.UTF_8);
    }

    /** 0..size-1 에서 k개를 중복 없이 뽑는다. */
    private static Set<Integer> sampleIds(int size, int k, Random rng) {
        Set<Integer> picked = new HashSet<>(k * 2);
        if (k >= size) {
            for (int i = 0; i < size; i++) {
                picked.add(i);
            }
            return picked;
        }
        while (picked.size() < k) {
            picked.add(rng.nextInt(size));
        }
        return picked;
    }

    public record AddressRecord(int id, String canonical, String alias) {
    }

    public record QueryRecord(String query, int expected_id) {
    }
}
