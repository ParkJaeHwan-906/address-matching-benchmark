package com.parkjaehwan.addressbench;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 의도적으로 단순한 O(N × |query| × |alias|) 기준선.
 * 모든 후보와 LCS 유사도를 계산하고 상위 k건만 힙으로 유지한다.
 * alias 정규화는 적재 시 한 번만 한다 — 실무 구현이라면 당연히 그렇게 하므로,
 * 매 질의마다 다시 정규화하던 Python 기준선보다 LCS에 유리한 조건이다.
 */
public final class ExhaustiveLcsMatcher implements Matcher {
    private final int[] ids;
    private final String[] aliases;
    private final char[][] normalized;

    public ExhaustiveLcsMatcher(int[] ids, String[] aliases) {
        if (ids.length != aliases.length) {
            throw new IllegalArgumentException("ids and aliases must have the same length");
        }
        this.ids = ids;
        this.aliases = aliases;
        this.normalized = new char[aliases.length][];
        for (int i = 0; i < aliases.length; i++) {
            normalized[i] = Normalizer.normalize(aliases[i]).toCharArray();
        }
    }

    public int size() {
        return ids.length;
    }

    @Override
    public SearchResult search(String query, int topK) {
        long started = System.nanoTime();
        char[] q = Normalizer.normalize(query).toCharArray();
        // 점수 → id → alias 순으로 큰 항목이 이긴다. 힙의 머리는 현재 top-k 중 가장 작은 항목.
        Comparator<Match> order = Comparator.comparingDouble(Match::score)
                .thenComparingInt(Match::addressId)
                .thenComparing(Match::alias);
        PriorityQueue<Match> heap = new PriorityQueue<>(topK + 1, order);
        CharArrayView left = new CharArrayView(q);
        CharArrayView right = new CharArrayView(null);
        for (int i = 0; i < ids.length; i++) {
            right.chars = normalized[i];
            double score = Lcs.similarityNormalized(left, right);
            if (heap.size() < topK) {
                heap.add(new Match(ids[i], aliases[i], score));
            } else {
                Match head = heap.peek();
                if (score > head.score() || (score == head.score() && (ids[i] > head.addressId()
                        || (ids[i] == head.addressId() && aliases[i].compareTo(head.alias()) > 0)))) {
                    heap.poll();
                    heap.add(new Match(ids[i], aliases[i], score));
                }
            }
        }
        List<Match> matches = new ArrayList<>(heap);
        matches.sort(order.reversed());
        return new SearchResult(matches, System.nanoTime() - started);
    }

    /** char[]를 복사 없이 CharSequence로 보는 얇은 뷰. */
    private static final class CharArrayView implements CharSequence {
        char[] chars;

        CharArrayView(char[] chars) {
            this.chars = chars;
        }

        @Override
        public int length() {
            return chars.length;
        }

        @Override
        public char charAt(int index) {
            return chars[index];
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new String(chars, start, end - start);
        }

        @Override
        public String toString() {
            return new String(chars);
        }
    }
}
