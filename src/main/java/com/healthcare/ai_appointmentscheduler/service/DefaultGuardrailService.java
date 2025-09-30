package com.healthcare.ai_appointmentscheduler.service;

import com.healthcare.ai_appointmentscheduler.dto.ParseResponse;
import com.healthcare.ai_appointmentscheduler.entity.AppointmentEntity;
import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
import com.healthcare.ai_appointmentscheduler.entity.NormalizedEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * DefaultGuardrailService
 *
 * Responsible for:
 *  - deciding final status ("ok" | "needs_clarification")
 *  - computing overall confidence from component confidences
 *  - assembling AppointmentEntity when status == ok
 *  - crafting helpful messages when clarification is needed
 */
@Service
public class DefaultGuardrailService {

    // weights used to compute overall confidence (tuneable)
    private static final double ENTITIES_WEIGHT = 0.60;
    private static final double NORMALIZATION_WEIGHT = 0.40;

    // penalty multipliers for missing critical information
    private static final double MISSING_DEPT_PENALTY = 0.5;
    private static final double MISSING_DATE_PENALTY = 0.7;
    private static final double MISSING_TIME_PENALTY = 0.8;

    // minimum thresholds that we consider "good enough" for entity/normalization confidences
    private static final double MIN_ENTITIES_CONF_FOR_OK = 0.6;
    private static final double MIN_NORMALIZATION_CONF_FOR_OK = 0.5;

    /**
     * Build a ParseResponse using the various pipeline outputs and confidences.
     *
     * @param rawText        original / cleaned text
     * @param entities       extracted entities (may contain null fields)
     * @param normalized     normalized entity (date/time) (may contain null fields)
     * @param entitiesConf   score [0..1] representing extraction confidence
     * @param normalizationConf score [0..1] representing normalization confidence
     * @return ParseResponse fully populated (status, message, confidences, appointment if ok)
     */
    public ParseResponse buildResponse(String rawText,
                                       ExtractedEntities entities,
                                       NormalizedEntity normalized,
                                       double entitiesConf,
                                       double normalizationConf) {

        ParseResponse resp = new ParseResponse();

        // basic fields
        resp.setRawText(rawText == null ? "" : rawText);
        resp.setEntities(entities);
        resp.setNormalized(normalized);

        // ensure component confidences are populated (use provided values; ensure within [0..1])
        entitiesConf = clamp01(entitiesConf);
        normalizationConf = clamp01(normalizationConf);
        resp.setEntitiesConfidence(entitiesConf);
        resp.setNormalizationConfidence(normalizationConf);

        // --- compute overall confidence (weighted) ---
        double overall = ENTITIES_WEIGHT * entitiesConf + NORMALIZATION_WEIGHT * normalizationConf;

        // Apply penalties for missing critical pieces (these multipliers reduce confidence)
        boolean hasDept = entities != null && entities.getDepartment() != null && !entities.getDepartment().isBlank();
        boolean hasDate = normalized != null && normalized.getDate() != null && !normalized.getDate().isBlank();
        boolean hasTime = normalized != null && normalized.getTime() != null && !normalized.getTime().isBlank();

        if (!hasDept) overall *= MISSING_DEPT_PENALTY;
        if (!hasDate) overall *= MISSING_DATE_PENALTY;
        if (!hasTime) overall *= MISSING_TIME_PENALTY;

        overall = clamp01(overall);
        resp.setConfidence(overall);

        // Optionally add structured breakdown if ParseResponse has such a field.
        // If you add a Map<String, Double> confidenceBreakdown to ParseResponse,
        // uncomment and use this block to fill it:
        //
        // Map<String, Double> breakdown = new HashMap<>();
        // breakdown.put("entities_confidence", entitiesConf);
        // breakdown.put("normalization_confidence", normalizationConf);
        // breakdown.put("overall", overall);
        // resp.setConfidenceBreakdown(breakdown);

        // --- Decide status & message ---
        List<String> missing = new ArrayList<>();
        if (!hasDept) missing.add("department");
        if (!hasDate) missing.add("date");
        if (!hasTime) missing.add("time");

        // success path: require that each core piece exists and confidences meet minimum thresholds
        boolean entityConfOk = entitiesConf >= MIN_ENTITIES_CONF_FOR_OK;
        boolean normConfOk = normalizationConf >= MIN_NORMALIZATION_CONF_FOR_OK;

        if (hasDept && hasDate && hasTime && entityConfOk && normConfOk) {
            // OK: assemble appointment
            resp.setStatus("ok");
            resp.setMessage("Appointment parsed successfully.");
            resp.setAppointment(assembleAppointment(entities, normalized));
            return resp;
        }

        // otherwise, needs clarification
        resp.setStatus("needs_clarification");

        // build friendly message
        String msg;
        if (missing.isEmpty()) {
            // all fields present but confidences low
            msg = String.format(Locale.ROOT,
                    "Low confidence (entities: %.2f, normalization: %.2f). Please confirm date/time/department.",
                    entitiesConf, normalizationConf);
        } else {
            msg = "Ambiguous " + String.join(", ", missing) + ".";
        }

        resp.setMessage(msg);

        // Optionally include suggestions: quick heuristics
        // e.g., if date missing, suggest "Tomorrow", "Next Friday", if time missing, suggest "09:00", "15:00", "18:00"
        // If you add a suggestions field to ParseResponse, populate it here.

        // appointment remains null in ambiguous cases
        resp.setAppointment(null);

        return resp;
    }

    private AppointmentEntity assembleAppointment(ExtractedEntities entities, NormalizedEntity normalized) {
        AppointmentEntity appointment = new AppointmentEntity();
        if (entities != null && entities.getDepartment() != null) {
            appointment.setDepartment(capitalize(entities.getDepartment()));
        } else {
            appointment.setDepartment(null);
        }
        if (normalized != null) {
            appointment.setDate(normalized.getDate());
            appointment.setTime(normalized.getTime());
            appointment.setTz(normalized.getTz());
        }
        return appointment;
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0,1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private double clamp01(double v) {
        if (Double.isNaN(v) || v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}






























//package com.healthcare.ai_appointmentscheduler.service;
//
//import com.healthcare.ai_appointmentscheduler.dto.ParseResponse;
//import com.healthcare.ai_appointmentscheduler.entity.AppointmentEntity;
//import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
//import com.healthcare.ai_appointmentscheduler.entity.NormalizedEntity;
//import org.springframework.stereotype.Service;
//
//import java.util.ArrayList;
//import java.util.List;
//
//@Service
//public class DefaultGuardrailService {
//
//    public ParseResponse buildResponse(String rawText, ExtractedEntities e,
//                                       NormalizedEntity n, double entityConf, double normConf) {
//        ParseResponse resp = new ParseResponse();
//        resp.setRawText(rawText);
//        resp.setConfidence(1.0);
//        resp.setEntities(e);
//        resp.setEntitiesConfidence(entityConf);
//        resp.setNormalized(n);
//        resp.setNormalizationConfidence(normConf);
//
//        boolean deptOk = e.getDepartment()!=null;
//        boolean dateOk = n.getDate()!=null;
//        boolean timeOk = n.getTime()!=null;
//
//        if (deptOk && dateOk && timeOk && entityConf>=0.6 && normConf>=0.5) {
//            resp.setStatus("ok");
//            resp.setMessage("Appointment parsed successfully.");
//            AppointmentEntity ap = new AppointmentEntity();
//            ap.setDepartment(capitalize(e.getDepartment()));
//            ap.setDate(n.getDate());
//            ap.setTime(n.getTime());
//            ap.setTz(n.getTz());
//            resp.setAppointment(ap);
//        } else {
//            resp.setStatus("needs_clarification");
//            List<String> issues = new ArrayList<>();
//            if (!deptOk) issues.add("department");
//            if (!dateOk) issues.add("date");
//            if (!timeOk) issues.add("time");
//            resp.setMessage("Ambiguous " + String.join(", ", issues) + ".");
//            resp.setAppointment(null);
//        }
//
//        return resp;
//    }
//
//    private String capitalize(String s) {
//        if (s==null||s.isEmpty()) return s;
//        return s.substring(0,1).toUpperCase() + s.substring(1).toLowerCase();
//    }
//}