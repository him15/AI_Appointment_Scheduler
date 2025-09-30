package com.healthcare.ai_appointmentscheduler.service;

import com.healthcare.ai_appointmentscheduler.config.DepartmentConfig;
import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
import com.healthcare.ai_appointmentscheduler.util.FuzzyMatcher;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entity extractor:
 * - department (controlled vocabulary) -> exact contains, then fuzzy fallback
 * - date_phrase (today/tomorrow/weekday/next <weekday>) -> fuzzy detection
 * - time_phrase (3pm/15:00/etc) -> tolerant regex + cleanup
 *
 * It returns ExtractedEntities with fields possibly null if not found.
 */
@Service
public class EntityExtractorImpl {

    // Tune thresholds here
    private static final double DEPT_SIM_THRESHOLD = 0.65; // similarity threshold for fuzzy department
    private static final int DEPT_EDIT_DISTANCE_MAX = 2;   // alternative acceptance rule

    // date-related vocabulary
    private static final List<String> DATE_WORDS = List.of(
            "today","tomorrow","yesterday","day after tomorrow",
            "monday","tuesday","wednesday","thursday","friday","saturday","sunday",
            "next","in" // 'next' will be checked in context
    );

    /**
     * Main extraction entrypoint.
     * Input should be preprocessed (lowercased / whitespace-normalized).
     */
    public ExtractedEntities extract(String raw) {
        ExtractedEntities out = new ExtractedEntities();
        if (raw == null) {
            return out;
        }

        String clean = normalizeOcrText(raw);

        // Department detection: prefer exact substring matches (controlled vocab), then fuzzy
        String dept = findDepartmentExact(clean);
        if (dept == null) dept = findDepartmentFuzzy(clean);
        out.setDepartment(dept);

        // Time phrase detection (raw phrase e.g. "3pm" or "3 pm")
        String timePhrase = findTimePhrase(clean);
        out.setTimePhrase(timePhrase);

        // Date phrase detection (e.g., "next friday", "tomorrow", "friday")
        String datePhrase = findDatePhrase(clean);
        out.setDatePhrase(datePhrase);

        return out;
    }

    // ----------------- department helpers -----------------

    private String findDepartmentExact(String clean) {
        if (clean == null || clean.isBlank()) return null;
        String low = clean.toLowerCase(Locale.ROOT);
        for (String dept : DepartmentConfig.DEPARTMENTS) {
            if (dept == null) continue;
            String dlow = dept.toLowerCase(Locale.ROOT);
            if (low.contains(dlow)) return dept; // return canonical name from config
        }
        return null;
    }

    private String findDepartmentFuzzy(String clean) {
        if (clean == null || clean.isBlank()) return null;
        // token windows approach (1..3 tokens)
        String sanitized = clean.replaceAll("[^a-z0-9\\s]", " ");
        String[] tokens = sanitized.split("\\s+");
        double bestSim = 0.0;
        String bestDept = null;

        for (int window = 1; window <= 3; window++) {
            for (int i = 0; i + window <= tokens.length; i++) {
                String candidate = String.join(" ", Arrays.copyOfRange(tokens, i, i + window)).trim();
                if (candidate.length() < 2) continue;
                for (String dept : DepartmentConfig.DEPARTMENTS) {
                    if (dept == null || dept.isBlank()) continue;
                    double sim = FuzzyMatcher.similarity(candidate, dept);
                    if (sim > bestSim) {
                        bestSim = sim;
                        bestDept = dept;
                    } else {
                        // also check edit distance as alternative accept
                        int dist = FuzzyMatcher.levenshtein(candidate, dept);
                        if (dist <= DEPT_EDIT_DISTANCE_MAX && dept.length() >= 4) {
                            // promote slightly if good edit distance
                            if (0.5 > bestSim) { // prefer any valid edit distance over tiny similarity
                                bestSim = 0.5;
                                bestDept = dept;
                            }
                        }
                    }
                }
            }
        }

        if (bestSim >= DEPT_SIM_THRESHOLD) return bestDept;
        return null;
    }

    // ----------------- time helpers -----------------

    /**
     * Find time phrase using tolerant regex and small OCR fixes.
     * returns a normalized phrase like "3pm" or "15:00" (time normalization lives in Normalizer)
     */
    private String findTimePhrase(String clean) {
        if (clean == null) return null;
        String s = clean.toLowerCase(Locale.ROOT);

        // common OCR confusions cleanup heuristics
        s = s.replaceAll("(?<=\\d)\\s*[pq]\\s*n", "pm"); // "p n" or "q n" -> pm
        s = s.replaceAll("(?<=\\d)\\s*p\\s*m", "pm");
        s = s.replaceAll("(?<=\\d)\\s*o\\s*m", "pm"); // o -> p
        s = s.replaceAll("(?<=\\d)\\s*g\\s*m", "pm"); // g -> p
        s = s.replaceAll("(?<=\\d)\\s*\\.", ""); // remove stray dots
        s = s.replaceAll("\\s+", " ");

        // tolerant patterns: 3pm | 3 pm | 15:00 | 3:30pm | 6 p m
        Pattern p = Pattern.compile("\\b(\\d{1,2})([:\\.]?(\\d{2}))?\\s*(am|pm)?\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(s);
        if (m.find()) {
            String hh = m.group(1);
            String mm = m.group(3);
            String ampm = m.group(4);
            StringBuilder sb = new StringBuilder();
            sb.append(hh);
            if (mm != null) sb.append(":").append(mm);
            if (ampm != null) sb.append(ampm);
            return sb.toString();
        }
        // fallback: look for standalone am/pm preceded by digit
        p = Pattern.compile("\\b(\\d{1,2})\\s*(am|pm)\\b", Pattern.CASE_INSENSITIVE);
        m = p.matcher(s);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    // ----------------- date helpers -----------------

    private String findDatePhrase(String clean) {
        if (clean == null) return null;
        String s = clean.toLowerCase(Locale.ROOT);

        // exact contains for common date words / weekdays
        for (String dw : DATE_WORDS) {
            if (s.contains(dw)) {
                // handle 'next <weekday>' detection
                if (dw.equals("next")) {
                    // try capture 'next friday' or fuzzy variant
                    Pattern pat = Pattern.compile("next\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)", Pattern.CASE_INSENSITIVE);
                    Matcher mm = pat.matcher(s);
                    if (mm.find()) {
                        return "next " + mm.group(1).toLowerCase();
                    }
                } else if (dw.matches("monday|tuesday|wednesday|thursday|friday|saturday|sunday")) {
                    return dw;
                } else {
                    // today / tomorrow / day after tomorrow etc.
                    if (s.contains("day after tomorrow")) return "day after tomorrow";
                    return dw;
                }
            }
        }

        // fuzzy search for windows like 'nxt friday' 'tommorrow' etc.
        String sanitized = s.replaceAll("[^a-z0-9\\s]", " ").trim();
        String[] tokens = sanitized.split("\\s+");
        double bestSim = 0.0;
        String bestMatch = null;

        for (int w = 1; w <= 2; w++) {
            for (int i = 0; i + w <= tokens.length; i++) {
                String cand = String.join(" ", Arrays.copyOfRange(tokens, i, i+w)).trim();
                if (cand.isEmpty()) continue;
                for (String dw : DATE_WORDS) {
                    double sim = FuzzyMatcher.similarity(cand, dw);
                    if (sim > bestSim) {
                        bestSim = sim;
                        bestMatch = dw;
                    }
                }
            }
        }
        // threshold: 0.6
        if (bestSim >= 0.60) {
            return bestMatch;
        }

        return null;
    }

    // ----------------- text normalization helpers -----------------

    /**
     * Basic OCR text cleaning to improve fuzzy matching (keeps letters/numbers and spaces,
     * converts common OCR errors).
     */
    private String normalizeOcrText(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("[\\uFEFF\\p{Cc}]", " "); // remove weird control chars
        s = s.toLowerCase(Locale.ROOT);

        // common OCR misreads to correct early (expand this list as you see errors)
        s = s.replaceAll("\\b1st\\b", "st"); // e.g. "1st" noise
        s = s.replaceAll("dent1st", "dentist"); // direct common pattern
        s = s.replaceAll("dnntist", "dentist");
        s = s.replaceAll("tomm?or?ow", "tomorrow");
        s = s.replaceAll("\\bnxt\\b", "next");
        s = s.replaceAll("\\bpn\\b", "pm");
        s = s.replaceAll("\\b0m\\b", "om"); // avoid accidental oddities
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