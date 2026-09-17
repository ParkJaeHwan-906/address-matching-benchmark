package com.parkjaehwan.addressbench;

import java.text.Normalizer.Form;

/**
 * 비교 전 문자열 정규화. NFKC 정규화 후 소문자로 바꾸고,
 * 숫자·영문·한글(자모·호환 자모·완성형) 이외의 문자는 모두 제거한다.
 */
public final class Normalizer {
    private Normalizer() {
    }

    public static String normalize(String value) {
        String lowered = java.text.Normalizer.normalize(value, Form.NFKC).toLowerCase();
        StringBuilder out = new StringBuilder(lowered.length());
        for (int i = 0; i < lowered.length(); i++) {
            char c = lowered.charAt(i);
            if (isKept(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isKept(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'z')
                || (c >= 'ᄀ' && c <= 'ᇿ')   // 한글 자모
                || (c >= '㄰' && c <= '㆏')   // 한글 호환 자모
                || (c >= '가' && c <= '힣');  // 한글 완성형
    }
}
