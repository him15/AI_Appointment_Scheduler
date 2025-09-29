package com.healthcare.ai_appointmentscheduler.service;

import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
import com.healthcare.ai_appointmentscheduler.entity.NormalizedEntity;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NattyNormalizer {

    private static final ZoneId TARGET_ZONE = ZoneId.of("Asia/Kolkata");

    public NormalizedEntity normalize(String cleanText, ExtractedEntities extracted) {
        NormalizedEntity normalized = new NormalizedEntity();

        try {
            Parser parser = new Parser();
            List<DateGroup> groups = parser.parse(cleanText);
            if (!groups.isEmpty() && !groups.get(0).getDates().isEmpty()) {
                java.util.Date d = groups.get(0).getDates().get(0);
                Instant inst = d.toInstant();
                ZonedDateTime zdt = inst.atZone(TARGET_ZONE);
                normalized.setDate(zdt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
                normalized.setTime(zdt.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
                        .format(DateTimeFormatter.ofPattern("HH:mm")));
                normalized.setTz(TARGET_ZONE.toString());
                return normalized;
            }
        } catch (Exception ex) {
            // fallback below
        }

        LocalDate today = LocalDate.now(TARGET_ZONE);
        for (DayOfWeek dow : DayOfWeek.values()) {
            if (cleanText.contains(dow.toString().toLowerCase(Locale.ROOT))) {
                int daysUntil = (dow.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
                if (cleanText.contains("next") || daysUntil == 0) daysUntil += 7;
                LocalDate target = today.plusDays(daysUntil);
                normalized.setDate(target.toString());
                break;
            }
        }

        Pattern timePattern = Pattern.compile("\\b(\\d{1,2}(:\\d{2})?\\s?(am|pm)?)\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = timePattern.matcher(cleanText);
        if (m.find()) {
            String tp = m.group().trim().toLowerCase(Locale.ROOT).replace(" ", "");
            int hour=0, min=0;
            if (tp.contains("am")||tp.contains("pm")) {
                String num = tp.replaceAll("[^0-9]","");
                if (!num.isEmpty()) hour = Integer.parseInt(num);
                if (tp.contains("pm") && hour < 12) hour += 12;
                if (tp.contains("am") && hour == 12) hour = 0;
            } else if (tp.contains(":")) {
                String[] parts = tp.split(":");
                hour = Integer.parseInt(parts[0]);
                min = Integer.parseInt(parts[1].replaceAll("[^0-9]",""));
            }
            normalized.setTime(String.format("%02d:%02d", hour, min));
        }

        if (normalized.getDate()!=null || normalized.getTime()!=null)
            normalized.setTz(TARGET_ZONE.toString());

        return normalized;
    }
}