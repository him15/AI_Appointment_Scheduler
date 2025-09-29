package com.healthcare.ai_appointmentscheduler.service;

import com.healthcare.ai_appointmentscheduler.config.DepartmentConfig;
import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EntityExtractorImpl {

    private static final Pattern TIME_PATTERN = Pattern.compile(
            "\\b(\\d{1,2}(:\\d{2})?\\s?(am|pm)?)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(today|tomorrow|day after tomorrow|in\\s+\\d+\\s+days|next\\s+\\w+day|\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b)",
            Pattern.CASE_INSENSITIVE);

    public ExtractedEntities extract(String cleanText) {
        ExtractedEntities e = new ExtractedEntities();

        // department
        String dept = DepartmentConfig.DEPARTMENTS.stream()
                .filter(cleanText::contains)
                .findFirst()
                .orElse(null);
        e.setDepartment(dept);

        // time
        Matcher tm = TIME_PATTERN.matcher(cleanText);
        if (tm.find()) e.setTimePhrase(tm.group().trim());

        // date
        Matcher dm = DATE_PATTERN.matcher(cleanText);
        if (dm.find()) {
            e.setDatePhrase(dm.group().trim());
        } else {
            if (cleanText.contains("soon") || cleanText.contains("asap") || cleanText.contains("anytime")) {
                e.setDatePhrase(null); // ambiguous
            }
        }
        return e;
    }
}