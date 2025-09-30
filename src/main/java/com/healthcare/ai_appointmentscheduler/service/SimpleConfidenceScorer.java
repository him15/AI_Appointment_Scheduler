package com.healthcare.ai_appointmentscheduler.service;

import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
import com.healthcare.ai_appointmentscheduler.entity.NormalizedEntity;
import org.springframework.stereotype.Service;

/**
 * Calculates confidence scores for the extraction and normalization steps.
 */
@Service
public class SimpleConfidenceScorer {

    /**
     * Scores the entity extraction step.
     * The score is based on which entities were found and the overall quality of the raw text.
     */
    public double scoreEntities(ExtractedEntities e, String rawText) {
        if (e == null) return 0.0;

        // Base score is weighted based on the presence of key entities.
        double conf = 0.0;
        if (e.getDepartment() != null) conf += 0.5; // Department is most important
        if (e.getDatePhrase() != null) conf += 0.3;
        if (e.getTimePhrase() != null) conf += 0.2;

        // The base score is then scaled by the text quality.
        // Gibberish text will result in a lower final score.
        double quality = computeTextQuality(rawText);
        conf *= (0.6 + 0.4 * quality);

        return Math.min(1.0, conf);
    }

    /**
     * Scores the normalization step.
     * The score is high if both date and time were successfully normalized.
     */
    public double scoreNormalization(NormalizedEntity n, String rawText) {
        if (n == null) return 0.0;

        double conf = 0.0;
        if (n.getDate() != null) conf += 0.5;
        if (n.getTime() != null) conf += 0.4;
        if (n.getTz() != null) conf += 0.1;

        return Math.min(1.0, conf);
    }

    /**
     * Helper to compute a text quality score (0.0 for gibberish, 1.0 for clean text).
     * Based on the ratio of letters and numbers to all characters.
     */
    private double computeTextQuality(String raw) {
        if (raw == null || raw.isBlank()) return 0.0;
        double alphaNum = raw.replaceAll("[^a-zA-Z0-9]", "").length();
        return alphaNum / (double) raw.length();
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