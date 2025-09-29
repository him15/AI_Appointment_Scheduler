package com.healthcare.ai_appointmentscheduler.service;

import com.healthcare.ai_appointmentscheduler.dto.ParseResponse;
import com.healthcare.ai_appointmentscheduler.entity.AppointmentEntity;
import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
import com.healthcare.ai_appointmentscheduler.entity.NormalizedEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultGuardrailService {

    public ParseResponse buildResponse(String rawText, ExtractedEntities e,
                                       NormalizedEntity n, double entityConf, double normConf) {
        ParseResponse resp = new ParseResponse();
        resp.setRawText(rawText);
        resp.setConfidence(1.0);
        resp.setEntities(e);
        resp.setEntitiesConfidence(entityConf);
        resp.setNormalized(n);
        resp.setNormalizationConfidence(normConf);

        boolean deptOk = e.getDepartment()!=null;
        boolean dateOk = n.getDate()!=null;
        boolean timeOk = n.getTime()!=null;

        if (deptOk && dateOk && timeOk && entityConf>=0.6 && normConf>=0.5) {
            resp.setStatus("ok");
            resp.setMessage("Appointment parsed successfully.");
            AppointmentEntity ap = new AppointmentEntity();
            ap.setDepartment(capitalize(e.getDepartment()));
            ap.setDate(n.getDate());
            ap.setTime(n.getTime());
            ap.setTz(n.getTz());
            resp.setAppointment(ap);
        } else {
            resp.setStatus("needs_clarification");
            List<String> issues = new ArrayList<>();
            if (!deptOk) issues.add("department");
            if (!dateOk) issues.add("date");
            if (!timeOk) issues.add("time");
            resp.setMessage("Ambiguous " + String.join(", ", issues) + ".");
            resp.setAppointment(null);
        }

        return resp;
    }

    private String capitalize(String s) {
        if (s==null||s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase() + s.substring(1).toLowerCase();
    }
}