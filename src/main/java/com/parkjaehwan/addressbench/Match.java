package com.parkjaehwan.addressbench;

/** 검색 결과 한 건. */
public record Match(int addressId, String alias, double score) {
}
