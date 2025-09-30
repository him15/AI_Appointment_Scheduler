package com.healthcare.ai_appointmentscheduler.service;

import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
import com.healthcare.ai_appointmentscheduler.entity.NormalizedEntity;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NattyNormalizer
 *
 * Responsibilities:
 *  - Try Natty parser first on a combined phrase (datePhrase + timePhrase or referenceText).
 *  - If Natty fails, use deterministic heuristics to resolve:
 *      * today / tomorrow / day after tomorrow
 *      * next <weekday>  (explicitly next week)
 *      * weekday name (maps to upcoming weekday, prefer this week unless 'next' used)
 *  - Parse time phrases robustly to HH:mm.
 *
 * Usage:
 *   NormalizedEntity normalized = nattyNormalizer.normalize(cleanText, extractedEntities);
 */
@Component
public class NattyNormalizer {

    private static final ZoneId TARGET_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Normalize entities into a NormalizedEntity.
     *
     * @param referenceText cleaned text from preprocessor (can be full raw text)
     * @param entities ExtractedEntities (may contain datePhrase / timePhrase)
     * @return NormalizedEntity with date/time/tz set when possible
     */
    public NormalizedEntity normalize(String referenceText, ExtractedEntities entities) {
        NormalizedEntity out = new NormalizedEntity();

        if (entities == null && (referenceText == null || referenceText.isBlank())) {
            return out;
        }

        // Prefer explicit entity phrases for natty input, otherwise use reference text
        String nattyInput = null;
        if (entities != null && entities.getDatePhrase() != null && !entities.getDatePhrase().isBlank()) {
            nattyInput = entities.getDatePhrase();
            if (entities.getTimePhrase() != null && !entities.getTimePhrase().isBlank()) {
                nattyInput = nattyInput + " " + entities.getTimePhrase();
            }
        } else if (referenceText != null && !referenceText.isBlank()) {
            nattyInput = referenceText;
        }

        // Try Natty first (with timezone)
        if (nattyInput != null && !nattyInput.isBlank()) {
            try {
                Parser parser = new Parser(TimeZone.getTimeZone(TARGET_ZONE));
                List<DateGroup> groups = parser.parse(nattyInput);
                if (!groups.isEmpty()) {
                    DateGroup dg = groups.get(0);
                    List<java.util.Date> dates = dg.getDates();
                    if (!dates.isEmpty()) {
                        java.util.Date parsed = dates.get(0);
                        Instant inst = parsed.toInstant();
                        ZonedDateTime zdt = inst.atZone(TARGET_ZONE);

                        // Natty may include time or only date; preserve both if present
                        out.setDate(zdt.toLocalDate().format(DATE_FMT));
                        out.setTime(zdt.toLocalTime().truncatedTo(ChronoUnit.MINUTES).format(TIME_FMT));
                        out.setTz(TARGET_ZONE.toString());
                        return out;
                    }
                }
            } catch (Exception ex) {
                // swallow and fall back to deterministic heuristics below
            }
        }

        // Deterministic fallback logic
        // Resolve date phrase first (if any)
        String datePhrase = entities == null ? null : entities.getDatePhrase();
        String timePhrase = entities == null ? null : entities.getTimePhrase();

        LocalDate today = LocalDate.now(TARGET_ZONE);
        LocalDate resolvedDate = null;

        if (datePhrase != null && !datePhrase.isBlank()) {
            String dp = datePhrase.toLowerCase(Locale.ROOT).trim();

            if (dp.contains("today")) {
                resolvedDate = today;
            } else if (dp.contains("tomorrow")) {
                resolvedDate = today.plusDays(1);
            } else if (dp.contains("day after tomorrow")) {
                resolvedDate = today.plusDays(2);
            } else {
                // detect explicit "next <weekday>"
                for (DayOfWeek dow : DayOfWeek.values()) {
                    String dowName = dow.toString().toLowerCase(Locale.ROOT);
                    if (dp.contains("next") && dp.contains(dowName)) {
                        // compute next week's day (strictly next week)
                        int daysUntil = daysUntilNextWeekday(today, dow, /*forceNextWeek=*/true);
                        resolvedDate = today.plusDays(daysUntil);
                        break;
                    }
                }

                // if not matched as next, check for weekday name (this week or upcoming)
                if (resolvedDate == null) {
                    for (DayOfWeek dow : DayOfWeek.values()) {
                        String dowName = dow.toString().toLowerCase(Locale.ROOT);
                        if (dp.contains(dowName)) {
                            int daysUntil = daysUntilNextWeekday(today, dow, /*forceNextWeek=*/false);
                            resolvedDate = today.plusDays(daysUntil);
                            break;
                        }
                    }
                }

                // handle "in N days"
                if (resolvedDate == null) {
                    Matcher m = Pattern.compile("in\\s+(\\d{1,2})\\s+days").matcher(dp);
                    if (m.find()) {
                        try {
                            int n = Integer.parseInt(m.group(1));
                            resolvedDate = today.plusDays(n);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        // If date not provided but referenceText might include date words, try a lightweight search
        if (resolvedDate == null && (referenceText != null && !referenceText.isBlank())) {
            String lowRef = referenceText.toLowerCase(Locale.ROOT);
            if (lowRef.contains("today")) resolvedDate = today;
            else if (lowRef.contains("tomorrow")) resolvedDate = today.plusDays(1);
            else if (lowRef.contains("day after tomorrow")) resolvedDate = today.plusDays(2);
            else {
                for (DayOfWeek dow : DayOfWeek.values()) {
                    String dowName = dow.toString().toLowerCase(Locale.ROOT);
                    if (lowRef.contains("next " + dowName)) {
                        int daysUntil = daysUntilNextWeekday(today, dow, true);
                        resolvedDate = today.plusDays(daysUntil);
                        break;
                    } else if (lowRef.contains(dowName)) {
                        int daysUntil = daysUntilNextWeekday(today, dow, false);
                        resolvedDate = today.plusDays(daysUntil);
                        break;
                    }
                }
            }
        }

        // Parse time
        String normalizedTime = null;
        if (timePhrase != null && !timePhrase.isBlank()) {
            normalizedTime = parseTimeToHHmm(timePhrase);
        } else if (referenceText != null && !referenceText.isBlank()) {
            normalizedTime = extractTimeFromText(referenceText);
        }

        if (resolvedDate != null) out.setDate(resolvedDate.format(DATE_FMT));
        if (normalizedTime != null) out.setTime(normalizedTime);
        if (out.getDate() != null || out.getTime() != null) out.setTz(TARGET_ZONE.toString());

        return out;
    }

    // Helper to compute days until the target weekday.
    // If forceNextWeek is true, we always return a positive number > 0 representing the next week's weekday.
    // If false, we return the days until this week's upcoming weekday (0 allowed meaning today).
    private int daysUntilNextWeekday(LocalDate today, DayOfWeek target, boolean forceNextWeek) {
        int todayVal = today.getDayOfWeek().getValue(); // Monday=1..Sunday=7
        int targetVal = target.getValue();
        int diff = (targetVal - todayVal + 7) % 7;
        if (forceNextWeek) {
            if (diff == 0) diff = 7;
            else diff = diff; // diff already days to this week's occurrence; if 0 -> next week
        }
        return diff;
    }

    // Parse time phrases like '3pm', '3:30pm', '15:00', '3 pm' into HH:mm
    private String parseTimeToHHmm(String timePhrase) {
        if (timePhrase == null || timePhrase.isBlank()) return null;
        String s = timePhrase.toLowerCase(Locale.ROOT).trim();
        s = s.replaceAll("\\s+", "");
        // allow "3pm", "3:30pm", "15:00"
        Pattern p = Pattern.compile("^(\\d{1,2})([:\\.]?(\\d{2}))?(am|pm)?$");
        Matcher m = p.matcher(s);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            String minPart = m.group(3);
            int minute = 0;
            if (minPart != null) minute = Integer.parseInt(minPart);
            String ampm = m.group(4);
            if (ampm != null) {
                if (ampm.equals("pm") && hour < 12) hour += 12;
                if (ampm.equals("am") && hour == 12) hour = 0;
            }
            hour = Math.max(0, Math.min(23, hour));
            minute = Math.max(0, Math.min(59, minute));
            return String.format("%02d:%02d", hour, minute);
        }
        return null;
    }

    // Try to find a time in a free-form string (first reasonable match)
    private String extractTimeFromText(String txt) {
        if (txt == null || txt.isBlank()) return null;
        String s = txt.toLowerCase(Locale.ROOT);
        // small cleanups of common OCR mistakes
        s = s.replaceAll("p\\s*n", "pm");
        s = s.replaceAll("p\\s*m", "pm");
        s = s.replaceAll("o\\s*m", "pm"); // occasional o->p OCR error
        // tolerant regex
        Pattern p = Pattern.compile("(\\d{1,2})([:\\.]?(\\d{2}))?\\s*(am|pm)?", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(s);
        if (m.find()) {
            String hh = m.group(1);
            String mm = m.group(3);
            String ampm = m.group(4);
            String combined = hh + (mm != null ? ":" + mm : "") + (ampm != null ? ampm : "");
            return parseTimeToHHmm(combined);
        }
        return null;
    }
}























//package com.healthcare.ai_appointmentscheduler.service;
//
//import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
//import com.healthcare.ai_appointmentscheduler.entity.NormalizedEntity;
//import com.joestelmach.natty.DateGroup;
//import com.joestelmach.natty.Parser;
//import org.springframework.stereotype.Service;
//
//import java.time.*;
//import java.time.format.DateTimeFormatter;
//import java.time.temporal.ChronoUnit;
//import java.util.List;
//import java.util.Locale;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//@Service
//public class NattyNormalizer {
//
//    private static final ZoneId TARGET_ZONE = ZoneId.of("Asia/Kolkata");
//
//    public NormalizedEntity normalize(String cleanText, ExtractedEntities extracted) {
//        NormalizedEntity normalized = new NormalizedEntity();
//
//        try {
//            Parser parser = new Parser();
//            List<DateGroup> groups = parser.parse(cleanText);
//            if (!groups.isEmpty() && !groups.get(0).getDates().isEmpty()) {
//                java.util.Date d = groups.get(0).getDates().get(0);
//                Instant inst = d.toInstant();
//                ZonedDateTime zdt = inst.atZone(TARGET_ZONE);
//                normalized.setDate(zdt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
//                normalized.setTime(zdt.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
//                        .format(DateTimeFormatter.ofPattern("HH:mm")));
//                normalized.setTz(TARGET_ZONE.toString());
//                return normalized;
//            }
//        } catch (Exception ex) {
//            // fallback below
//        }
//
//        LocalDate today = LocalDate.now(TARGET_ZONE);
//        for (DayOfWeek dow : DayOfWeek.values()) {
//            if (cleanText.contains(dow.toString().toLowerCase(Locale.ROOT))) {
//                int daysUntil = (dow.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
//                if (cleanText.contains("next") || daysUntil == 0) daysUntil += 7;
//                LocalDate target = today.plusDays(daysUntil);
//                normalized.setDate(target.toString());
//                break;
//            }
//        }
//
//        Pattern timePattern = Pattern.compile("\\b(\\d{1,2}(:\\d{2})?\\s?(am|pm)?)\\b", Pattern.CASE_INSENSITIVE);
//        Matcher m = timePattern.matcher(cleanText);
//        if (m.find()) {
//            String tp = m.group().trim().toLowerCase(Locale.ROOT).replace(" ", "");
//            int hour=0, min=0;
//            if (tp.contains("am")||tp.contains("pm")) {
//                String num = tp.replaceAll("[^0-9]","");
//                if (!num.isEmpty()) hour = Integer.parseInt(num);
//                if (tp.contains("pm") && hour < 12) hour += 12;
//                if (tp.contains("am") && hour == 12) hour = 0;
//            } else if (tp.contains(":")) {
//                String[] parts = tp.split(":");
//                hour = Integer.parseInt(parts[0]);
//                min = Integer.parseInt(parts[1].replaceAll("[^0-9]",""));
//            }
//            normalized.setTime(String.format("%02d:%02d", hour, min));
//        }
//
//        if (normalized.getDate()!=null || normalized.getTime()!=null)
//            normalized.setTz(TARGET_ZONE.toString());
//
//        return normalized;
//    }
//}