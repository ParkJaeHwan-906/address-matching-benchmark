package com.parkjaehwan.addressbench;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LcsTest {
    @Test
    void lcsLength() {
        assertEquals(5, Lcs.length("강남래미안", "강남구래미안"));
    }

    @Test
    void similarityIgnoresSpacesAndPunctuation() {
        assertEquals(1.0, Lcs.similarity("서울 강남구-래미안", "서울강남구래미안"));
    }

    @Test
    void emptyStringsAreIdentical() {
        assertEquals(1.0, Lcs.similarity("", "---"));
    }

    @Test
    void normalizerKeepsHangulDigitsAndLowercaseLatin() {
        assertEquals("서울apt12", Normalizer.normalize("서울 APT-12!"));
    }
}
