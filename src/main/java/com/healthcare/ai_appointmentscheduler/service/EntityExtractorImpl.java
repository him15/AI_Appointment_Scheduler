package com.healthcare.ai_appointmentscheduler.service;

import com.healthcare.ai_appointmentscheduler.config.DepartmentConfig;
import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
import com.healthcare.ai_appointmentscheduler.util.FuzzyMatcher;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EntityExtractorImpl {

    private static final double DEPT_SIM_THRESHOLD = 0.75; // Stricter threshold for fuzzy matches
    private static final double DATE_SIM_THRESHOLD = 0.70;

    private static final List<String> DATE_WORDS = List.of(
            "today", "tomorrow", "day after tomorrow",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
    );

    public ExtractedEntities extract(String raw) {
        ExtractedEntities out = new ExtractedEntities();
        if (raw == null) {
            return out;
        }

        String clean = normalizeOcrText(raw);

        DeptMatchResult deptMatch = findDepartment(clean);
        out.setDepartment(deptMatch.department());
        out.setDepartmentConfidence(deptMatch.confidence());

        out.setTimePhrase(findTimePhrase(clean));
        out.setDatePhrase(findDatePhrase(clean));

        return out;
    }

    private record DeptMatchResult(String department, double confidence) {}

    /**
     * --- NEW, MORE ACCURATE DEPARTMENT FINDER ---
     * This version prioritizes exact, whole-word matches over simple substring contains.
     */
    private DeptMatchResult findDepartment(String clean) {
        if (clean == null || clean.isBlank()) return new DeptMatchResult(null, 0.0);

        // 1. Tokenize the input to work with whole words.
        String[] tokens = clean.split("\\s+");
        Set<String> inputTokens = new HashSet<>(Arrays.asList(tokens));

        // 2. Try for an exact WHOLE WORD match first. This is a true 100% confidence match.
        for (String dept : DepartmentConfig.DEPARTMENTS_SORTED_FOR_SEARCH) {
            if (inputTokens.contains(dept)) {
                return new DeptMatchResult(dept, 1.0);
            }
        }

        // 3. If no exact whole word is found, THEN use fuzzy matching as a fallback.
        double bestSim = 0.0;
        String bestDept = null;

        for (int window = 1; window <= 2; window++) {
            for (int i = 0; i + window <= tokens.length; i++) {
                String candidate = String.join(" ", Arrays.copyOfRange(tokens, i, i + window)).trim();
                if (!FuzzyMatcher.isPlausibleWord(candidate)) continue; // Skip gibberish

                for (String dept : DepartmentConfig.DEPARTMENTS) {
                    double sim = FuzzyMatcher.similarity(candidate, dept);
                    if (sim > bestSim) {
                        bestSim = sim;
                        bestDept = dept;
                    }
                }
            }
        }

        if (bestSim >= DEPT_SIM_THRESHOLD) {
            return new DeptMatchResult(bestDept, bestSim);
        }

        return new DeptMatchResult(null, 0.0);
    }

    private String findDatePhrase(String clean) {
        if (clean == null || clean.isBlank()) return null;
        if (clean.contains("day after tomorrow")) return "day after tomorrow";
        Pattern nextWeekdayPattern = Pattern.compile("next\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)");
        Matcher nextMatcher = nextWeekdayPattern.matcher(clean);
        if (nextMatcher.find()) return nextMatcher.group(0);
        if (clean.contains("tomorrow")) return "tomorrow";
        if (clean.contains("today")) return "today";
        for (String day : List.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")) {
            if (clean.contains(day)) return day;
        }
        String[] tokens = clean.replaceAll("[^a-z\\s]", "").split("\\s+");
        double bestSim = 0.0;
        String bestMatch = null;
        for (String token : tokens) {
            for (String dateWord : DATE_WORDS) {
                double sim = FuzzyMatcher.similarity(token, dateWord);
                if (sim > bestSim) {
                    bestSim = sim;
                    bestMatch = dateWord;
                }
            }
        }
        if (bestSim >= DATE_SIM_THRESHOLD) return bestMatch;
        return null;
    }

    private String findTimePhrase(String clean) {
        if (clean == null) return null;
        String s = clean.toLowerCase(Locale.ROOT);
        s = s.replaceAll("(?<=\\d)\\s*p\\s*m", "pm");
        s = s.replaceAll("\\s+", "");
        Pattern p = Pattern.compile("(\\d{1,2}(:\\d{2})?(am|pm))");
        Matcher m = p.matcher(s);
        if (m.find()) return m.group(1);
        return null;
    }

    private String normalizeOcrText(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase(Locale.ROOT);
        s = s.replaceAll("\\btomorw\\b", "tomorrow");
        s = s.replaceAll("tomm?or?ow", "tomorrow");
        s = s.replaceAll("\\bnxt\\b", "next");
        s = s.replaceAll("[^a-z0-9:\\s]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }
}
























//package com.healthcare.ai_appointmentscheduler.service;
//
//import com.healthcare.ai_appointmentscheduler.config.DepartmentConfig;
//import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
//import org.springframework.stereotype.Service;
//
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//@Service
//public class EntityExtractorImpl {
//
//    private static final Pattern TIME_PATTERN = Pattern.compile(
//            "\\b(\\d{1,2}(:\\d{2})?\\s?(am|pm)?)\\b", Pattern.CASE_INSENSITIVE);
//
//    private static final Pattern DATE_PATTERN = Pattern.compile(
//            "(today|tomorrow|day after tomorrow|in\\s+\\d+\\s+days|next\\s+\\w+day|\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b)",
//            Pattern.CASE_INSENSITIVE);
//
//    public ExtractedEntities extract(String cleanText) {
//        ExtractedEntities e = new ExtractedEntities();
//
//        // department
//        String dept = DepartmentConfig.DEPARTMENTS.stream()
//                .filter(cleanText::contains)
//                .findFirst()
//                .orElse(null);
//        e.setDepartment(dept);
//
//        // time
//        Matcher tm = TIME_PATTERN.matcher(cleanText);
//        if (tm.find()) e.setTimePhrase(tm.group().trim());
//
//        // date
//        Matcher dm = DATE_PATTERN.matcher(cleanText);
//        if (dm.find()) {
//            e.setDatePhrase(dm.group().trim());
//        } else {
//            if (cleanText.contains("soon") || cleanText.contains("asap") || cleanText.contains("anytime")) {
//                e.setDatePhrase(null); // ambiguous
//            }
//        }
//        return e;
//    }
//}