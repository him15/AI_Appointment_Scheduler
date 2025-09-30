package com.healthcare.ai_appointmentscheduler.service;

import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
import com.healthcare.ai_appointmentscheduler.entity.NormalizedEntity;
import org.springframework.stereotype.Service;

/**
 * Improved confidence scorer:
 * - Entities confidence depends on presence of department, date, time + OCR quality.
 * - Normalization confidence depends on valid date/time/tz and sanity checks.
 */
@Service
public class SimpleConfidenceScorer {

    /**
     * Score entity extraction confidence (0–1).
     * Uses weights + OCR quality scaling.
     */
    public double scoreEntities(ExtractedEntities e, String rawText) {
        if (e == null) return 0.0;

        double conf = 0.0;
        if (e.getDepartment() != null) conf += 0.4;
        if (e.getDatePhrase() != null) conf += 0.3;
        if (e.getTimePhrase() != null) conf += 0.3;

        // scale with OCR quality factor
        double quality = computeOcrQuality(rawText);
        conf *= (0.5 + 0.5 * quality); // if text is gibberish, reduce score

        return Math.min(1.0, conf);
    }

    /**
     * Score normalization confidence (0–1).
     * Stronger if both date + time are present and look valid.
     */
    public double scoreNormalization(NormalizedEntity n, String rawText) {
        if (n == null) return 0.0;

        double conf = 0.0;
        if (n.getDate() != null) conf += 0.5;
        if (n.getTime() != null) conf += 0.4;
        if (n.getTz() != null) conf += 0.1;

        // sanity check: avoid false 00:00 when no AM/PM context
        if ("00:00".equals(n.getTime()) &&
                (rawText == null || !rawText.toLowerCase().contains("12"))) {
            conf *= 0.5;
        }

        return Math.min(1.0, conf);
    }

    // ---------------- Helper ----------------

    /**
     * OCR quality factor: ratio of alphanumeric chars to total length.
     * Returns 0 (poor) → 1 (good).
     */
    private double computeOcrQuality(String raw) {
        if (raw == null || raw.isBlank()) return 0.0;
        double alphaNum = raw.replaceAll("[^a-zA-Z0-9]", "").length();
        double ratio = alphaNum / (double) raw.length();
        return Math.max(0.0, Math.min(1.0, ratio));
    }
}


















//package com.healthcare.ai_appointmentscheduler.service;






//
//import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
//import com.healthcare.ai_appointmentscheduler.entity.NormalizedEntity;
//import org.springframework.stereotype.Service;
//
//@Service
//public class SimpleConfidenceScorer {
//
//    public double scoreEntities(ExtractedEntities e) {
//        double conf = 0.6;
//        if (e.getDepartment()!=null) conf += 0.15;
//        if (e.getDatePhrase()!=null) conf += 0.15;
//        if (e.getTimePhrase()!=null) conf += 0.10;
//        return Math.min(conf, 1.0);
//    }
//
//    public double scoreNormalization(NormalizedEntity n) {
//        if (n==null) return 0.0;
//        return (n.getDate()!=null && n.getTime()!=null) ? 0.9 : 0.0;
//    }
//}