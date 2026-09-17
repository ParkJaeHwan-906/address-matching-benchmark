package com.parkjaehwan.addressbench;

/**
 * 최장 공통 부분열(LCS). 두 행만 쓰는 O(min(m,n)) 메모리 DP.
 */
public final class Lcs {
    private Lcs() {
    }

    public static int length(CharSequence left, CharSequence right) {
        if (left.length() > right.length()) {
            CharSequence swap = left;
            left = right;
            right = swap;
        }
        int m = left.length();
        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];
        for (int j = 0; j < right.length(); j++) {
            char rc = right.charAt(j);
            current[0] = 0;
            for (int i = 1; i <= m; i++) {
                if (left.charAt(i - 1) == rc) {
                    current[i] = previous[i - 1] + 1;
                } else {
                    current[i] = Math.max(previous[i], current[i - 1]);
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[m];
    }

    /** 정규화한 두 문자열의 LCS 길이를 긴 쪽 길이로 나눈 값. 둘 다 비어 있으면 1.0. */
    public static double similarity(String left, String right) {
        return similarityNormalized(Normalizer.normalize(left), Normalizer.normalize(right));
    }

    /** 이미 정규화된 두 문자열에 대한 유사도. 전수 탐색 루프에서 쓴다. */
    public static double similarityNormalized(CharSequence left, CharSequence right) {
        int denominator = Math.max(left.length(), right.length());
        return denominator == 0 ? 1.0 : (double) length(left, right) / denominator;
    }
}
