package com.parkjaehwan.addressbench;

import java.util.List;

/** 검색 방식 공통 인터페이스. 지연시간은 각 구현이 검색 호출 자체만 잰다. */
public interface Matcher {
    SearchResult search(String query, int topK) throws Exception;

    default SearchResult search(String query) throws Exception {
        return search(query, 5);
    }

    record SearchResult(List<Match> matches, long elapsedNanos) {
    }
}
