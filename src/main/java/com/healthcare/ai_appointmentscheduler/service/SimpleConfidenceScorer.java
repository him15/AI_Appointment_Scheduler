package com.healthcare.ai_appointmentscheduler.service;

import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
import com.healthcare.ai_appointmentscheduler.entity.NormalizedEntity;
import org.springframework.stereotype.Service;

@Service
public class SimpleConfidenceScorer {

    public double scoreEntities(ExtractedEntities e) {
        double conf = 0.6;
        if (e.getDepartment()!=null) conf += 0.15;
        if (e.getDatePhrase()!=null) conf += 0.15;
        if (e.getTimePhrase()!=null) conf += 0.10;
        return Math.min(conf, 1.0);
    }

    public double scoreNormalization(NormalizedEntity n) {
        if (n==null) return 0.0;
        return (n.getDate()!=null && n.getTime()!=null) ? 0.9 : 0.0;
    }
}