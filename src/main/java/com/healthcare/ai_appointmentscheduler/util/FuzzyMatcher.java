package com.healthcare.ai_appointmentscheduler.util;

/**
 * Utility for Levenshtein distance, normalized similarity, and word plausibility checks.
 */
public final class FuzzyMatcher {

    private FuzzyMatcher() {}

    /**
     * New method: A simple heuristic to check if a word is plausible or just gibberish.
     * Real words have a reasonable mix of vowels and consonants.
     * @param word The word to check.
     * @return true if the word seems plausible, false otherwise.
     */
    public static boolean isPlausibleWord(String word) {
        if (word == null || word.length() < 3) {
            return true; // Too short to judge, let it pass
        }
        word = word.toLowerCase();

        int consecutiveConsonants = 0;
        int maxConsecutiveConsonants = 0;
        int vowelCount = 0;

        for (char c : word.toCharArray()) {
            if ("aeiou".indexOf(c) >= 0) {
                vowelCount++;
                consecutiveConsonants = 0;
            } else if (Character.isLetter(c)) {
                consecutiveConsonants++;
            }
            if (consecutiveConsonants > maxConsecutiveConsonants) {
                maxConsecutiveConsonants = consecutiveConsonants;
            }
        }

        // A word is likely gibberish if it has no vowels or has a long string of consonants.
        if (vowelCount == 0 && word.length() > 2) return false;
        if (maxConsecutiveConsonants >= 5) return false; // e.g., "fsdfgds"

        return true;
    }

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