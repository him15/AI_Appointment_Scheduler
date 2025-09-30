package com.healthcare.ai_appointmentscheduler.util;

/**
 * Small utility: Levenshtein distance + normalized similarity.
 * Fast, memory-efficient iterative implementation.
 */
public final class FuzzyMatcher {

    private FuzzyMatcher() {}

    public static int levenshtein(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        a = a.toLowerCase();
        b = b.toLowerCase();
        if (a.equals(b)) return 0;
        if (a.length() == 0) return b.length();
        if (b.length() == 0) return a.length();

        if (a.length() < b.length()) {
            String tmp = a; a = b; b = tmp;
        }

        int[] prev = new int[b.length() + 1];
        int[] cur  = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= b.length(); j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j-1] + 1, prev[j] + 1), prev[j-1] + cost);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[b.length()];
    }

    /**
     * Normalized similarity: 1.0 = identical, 0.0 = totally different.
     */
    public static double similarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        a = a.trim();
        b = b.trim();
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        int dist = levenshtein(a, b);
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 1.0;
        return 1.0 - (double) dist / (double) max;
    }
}