package com.healthcare.ai_appointmentscheduler.service;

import com.healthcare.ai_appointmentscheduler.dto.ParseResponse;
import com.healthcare.ai_appointmentscheduler.entity.AppointmentEntity;
import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
import com.healthcare.ai_appointmentscheduler.entity.NormalizedEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DefaultGuardrailService {

    // --- NEW, SIMPLER THRESHOLD ---
    // Any department match, whether exact or fuzzy, must have a confidence
    // score above this minimum floor to be considered valid.
    private static final double MIN_DEPT_CONFIDENCE_FLOOR = 0.70; // 70%

    public ParseResponse buildResponse(String rawText,
                                       ExtractedEntities entities,
                                       NormalizedEntity normalized,
                                       double entitiesConf,
                                       double normalizationConf) {

        ParseResponse resp = new ParseResponse();
        resp.setRawText(rawText == null ? "" : rawText);
        resp.setEntities(entities);
        resp.setNormalized(normalized);
        resp.setEntitiesConfidence(entitiesConf);
        resp.setNormalizationConfidence(normalizationConf);

        // --- REFACTORED AND SIMPLIFIED LOGIC ---

        boolean hasDept = entities != null && entities.getDepartment() != null;
        boolean hasDatePhrase = entities != null && entities.getDatePhrase() != null;
        boolean hasTime = normalized != null && normalized.getTime() != null;

        // A department is considered "valid" if it was found AND its confidence is above our floor.
        boolean departmentIsValid = hasDept && entities.getDepartmentConfidence() >= MIN_DEPT_CONFIDENCE_FLOOR;

        // The final confidence score calculation remains the same.
        double overallConfidence = 0.7 * entitiesConf + 0.3 * normalizationConf;
        if (!departmentIsValid) overallConfidence *= 0.2;
        if (!hasDatePhrase) overallConfidence *= 0.4;
        resp.setConfidence(clamp01(overallConfidence));

        // The final decision is now much cleaner and more direct.
        if (departmentIsValid && hasDatePhrase && hasTime) {
            resp.setStatus("ok");
            resp.setMessage("Appointment parsed successfully.");
            resp.setAppointment(assembleAppointment(entities, normalized));
        } else {
            resp.setStatus("needs_clarification");
            List<String> missing = new ArrayList<>();
            if (!departmentIsValid) missing.add("department");
            if (!hasDatePhrase) missing.add("date");
            if (!hasTime) missing.add("time");
            resp.setMessage("Ambiguous " + String.join(", ", missing) + ".");
            resp.setAppointment(null);
        }

        return resp;
    }

    private AppointmentEntity assembleAppointment(ExtractedEntities entities, NormalizedEntity normalized) {
        AppointmentEntity appointment = new AppointmentEntity();
        if (entities != null && entities.getDepartment() != null) {
            appointment.setDepartment(capitalize(entities.getDepartment()));
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
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
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