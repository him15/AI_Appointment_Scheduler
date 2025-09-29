package com.healthcare.ai_appointmentscheduler.service.impl;

import com.healthcare.ai_appointmentscheduler.config.DepartmentConfig;
import com.healthcare.ai_appointmentscheduler.dto.ParseResponse;
import com.healthcare.ai_appointmentscheduler.entity.AppointmentEntity;
import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
import com.healthcare.ai_appointmentscheduler.entity.NormalizedEntity;
import com.healthcare.ai_appointmentscheduler.service.PipelineService;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PipelineServiceImpl implements PipelineService {

    private static final ZoneId TARGET_ZONE = ZoneId.of("Asia/Kolkata");

    // abbreviation map used during preprocessing
    private static final Map<String, String> REPLACEMENTS = Map.ofEntries(
            Map.entry("tmrw", "tomorrow"),
            Map.entry("nxt", "next"),
            Map.entry("mon", "monday"),
            Map.entry("tue", "tuesday"),
            Map.entry("tues", "tuesday"),
            Map.entry("wed", "wednesday"),
            Map.entry("thu", "thursday"),
            Map.entry("thur", "thursday"),
            Map.entry("fri", "friday"),
            Map.entry("sat", "saturday"),
            Map.entry("sun", "sunday")
    );

    // public entry point called by controller
    @Override
    public ParseResponse parseText(String text) {
        ParseResponse resp = new ParseResponse();
        resp.setRawText(text);
        resp.setConfidence(1.0); // for typed input

        // default safe values
        resp.setStatus("needs_clarification");
        resp.setMessage("Ambiguous input");

        // Step A: preprocess
        String cleanText = preprocessText(text);

        // Step B: extract entities
        ExtractedEntities entities = extractEntities(cleanText);
        resp.setEntities(entities);

        // Step C: calculate entity confidence
        double entitiesConf = calculateEntitiesConfidence(entities);
        resp.setEntitiesConfidence(entitiesConf);

        // Step D: normalization (Natty)
        NormalizedEntity normalized = normalizeEntities(cleanText);
        resp.setNormalized(normalized);
        double normalizationConf = (normalized.getDate() != null && normalized.getTime() != null) ? 0.9 : 0.0;
        resp.setNormalizationConfidence(normalizationConf);

        // Step E: decide final status & message
        buildStatusAndMessage(resp, entities, normalized, entitiesConf, normalizationConf);

        // Step F: assemble appointment only if status ok
        if ("ok".equals(resp.getStatus())) {
            AppointmentEntity appointment = assembleAppointment(entities, normalized);
            resp.setAppointment(appointment);
        } else {
            resp.setAppointment(null);
        }

        return resp;
    }

    // ---------------------- Helper methods ----------------------

    private String preprocessText(String text) {
        if (text == null) return "";
        String cleaned = text.toLowerCase(Locale.ROOT);
        // expand common abbreviations
        for (Map.Entry<String, String> e : REPLACEMENTS.entrySet()) {
            cleaned = cleaned.replace(e.getKey(), e.getValue());
        }
        // normalize multiple spaces
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private ExtractedEntities extractEntities(String cleanText) {
        ExtractedEntities entities = new ExtractedEntities();

        // department detection (controlled vocabulary)
        String foundDept = DepartmentConfig.DEPARTMENTS.stream()
                .filter(cleanText::contains)
                .findFirst()
                .orElse(null);
        entities.setDepartment(foundDept);

        // time phrase detection (e.g., "3pm", "3 pm", "15:30")
        Pattern timePattern = Pattern.compile("\\b(\\d{1,2}(:\\d{2})?\\s?(am|pm)?)\\b", Pattern.CASE_INSENSITIVE);
        Matcher timeMatcher = timePattern.matcher(cleanText);
        if (timeMatcher.find()) {
            entities.setTimePhrase(timeMatcher.group().trim());
        }

        // date phrase detection (weekdays, today/tomorrow, next <weekday>, in N days, day after tomorrow)
        Pattern datePattern = Pattern.compile("(today|tomorrow|day after tomorrow|in\\s+\\d+\\s+days|next\\s+\\w+day|\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b)",
                Pattern.CASE_INSENSITIVE);
        Matcher dateMatcher = datePattern.matcher(cleanText);
        if (dateMatcher.find()) {
            entities.setDatePhrase(dateMatcher.group().trim());
        } else {
            // if user wrote vague terms like "soon", treat as ambiguous (leave null)
            if (cleanText.contains("soon") || cleanText.contains("asap") || cleanText.contains("anytime")) {
                entities.setDatePhrase(null);
            }
        }
        return entities;
    }

    private double calculateEntitiesConfidence(ExtractedEntities entities) {
        double conf = 0.6;
        if (entities.getDepartment() != null) conf += 0.15;
        if (entities.getDatePhrase() != null) conf += 0.15;
        if (entities.getTimePhrase() != null) conf += 0.10;
        return Math.min(conf, 1.0);
    }

    private NormalizedEntity normalizeEntities(String cleanText) {
        NormalizedEntity normalized = new NormalizedEntity();

        // Use Natty to parse free text if possible
        try {
            Parser parser = new Parser();
            List<DateGroup> groups = parser.parse(cleanText);

            if (!groups.isEmpty() && !groups.get(0).getDates().isEmpty()) {
                java.util.Date parsedDate = groups.get(0).getDates().get(0);
                Instant inst = parsedDate.toInstant();
                ZonedDateTime zdt = inst.atZone(TARGET_ZONE);

                normalized.setDate(zdt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
                normalized.setTime(zdt.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
                        .format(DateTimeFormatter.ofPattern("HH:mm")));
                normalized.setTz(TARGET_ZONE.toString());
                return normalized;
            }
        } catch (Exception e) {
            // parser errors should not crash the service; fall through to heuristics
        }

        // Fallback heuristics (if Natty failed)
        // - try to detect date via simple weekday or keywords in the cleaned text
        LocalDate today = LocalDate.now(TARGET_ZONE);

        // weekday match
        for (DayOfWeek dow : DayOfWeek.values()) {
            String dowLower = dow.toString().toLowerCase(Locale.ROOT);
            if (cleanText.contains(dowLower)) {
                int daysUntil = (dow.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
                // if phrase contains "next", push to next week
                if (cleanText.contains("next") || daysUntil == 0) daysUntil += 7;
                LocalDate target = today.plusDays(daysUntil);
                normalized.setDate(target.toString());
                break;
            }
        }

        // simple time parse fallback
        Pattern timePattern = Pattern.compile("\\b(\\d{1,2}(:\\d{2})?\\s?(am|pm)?)\\b", Pattern.CASE_INSENSITIVE);
        Matcher timeMatcher = timePattern.matcher(cleanText);
        if (timeMatcher.find()) {
            String tp = timeMatcher.group().trim().toLowerCase(Locale.ROOT).replace(" ", "");
            int hour = 0, minute = 0;
            if (tp.contains("am") || tp.contains("pm")) {
                String numPart = tp.replaceAll("[^0-9]", "");
                if (!numPart.isEmpty()) {
                    hour = Integer.parseInt(numPart);
                    if (tp.contains("pm") && hour < 12) hour += 12;
                    if (tp.contains("am") && hour == 12) hour = 0;
                }
            } else if (tp.contains(":")) {
                String[] parts = tp.split(":");
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
            }
            LocalTime time = LocalTime.of(hour, minute);
            normalized.setTime(time.format(DateTimeFormatter.ofPattern("HH:mm")));
        }

        // set timezone only if something was set
        if (normalized.getDate() != null || normalized.getTime() != null) {
            normalized.setTz(TARGET_ZONE.toString());
        }

        return normalized;
    }

    private void buildStatusAndMessage(ParseResponse resp, ExtractedEntities entities, NormalizedEntity normalized,
                                       double entitiesConf, double normalizationConf) {
        boolean deptOk = entities.getDepartment() != null;
        boolean dateOk = normalized.getDate() != null;
        boolean timeOk = normalized.getTime() != null;

        // success path
        if (deptOk && dateOk && timeOk && entitiesConf >= 0.6 && normalizationConf >= 0.5) {
            resp.setStatus("ok");
            resp.setMessage("Appointment parsed successfully.");
            return;
        }

        // otherwise build helpful message
        resp.setStatus("needs_clarification");
        List<String> issues = new ArrayList<>();
        if (!deptOk) issues.add("department");
        if (!dateOk) {
            // hint for ambiguous tokens
            String hint = "date";
            // if original raw_text was vague we might add detail later - currently general
            issues.add(hint);
        }
        if (!timeOk) issues.add("time");

        if (issues.isEmpty()) {
            resp.setMessage("Needs clarification.");
        } else {
            resp.setMessage("Ambiguous " + String.join(", ", issues) + ".");
        }
    }

    private AppointmentEntity assembleAppointment(ExtractedEntities entities, NormalizedEntity normalized) {
        AppointmentEntity appointment = new AppointmentEntity();
        String dept = entities.getDepartment();
        appointment.setDepartment(dept != null ? capitalize(dept) : null);
        appointment.setDate(normalized.getDate());
        appointment.setTime(normalized.getTime());
        appointment.setTz(normalized.getTz());
        return appointment;
    }

    // small util
    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}