package com.healthcare.ai_appointmentscheduler.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
import com.healthcare.ai_appointmentscheduler.entity.NormalizedEntity;
import com.healthcare.ai_appointmentscheduler.entity.AppointmentEntity;

public class ParseResponse {
    @JsonProperty("raw_text")
    private String rawText;

    private double confidence;

    private ExtractedEntities entities;

    @JsonProperty("entities_confidence")
    private Double entitiesConfidence;

    private NormalizedEntity normalized;

    @JsonProperty("normalization_confidence")
    private Double normalizationConfidence;

    private AppointmentEntity appointment;

    private String status;

    private String message; // 👈 add this

    // ---------------- Getters & setters ----------------

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public ExtractedEntities getEntities() { return entities; }
    public void setEntities(ExtractedEntities entities) { this.entities = entities; }

    public Double getEntitiesConfidence() { return entitiesConfidence; }
    public void setEntitiesConfidence(Double entitiesConfidence) { this.entitiesConfidence = entitiesConfidence; }

    public NormalizedEntity getNormalized() { return normalized; }
    public void setNormalized(NormalizedEntity normalized) { this.normalized = normalized; }

    public Double getNormalizationConfidence() { return normalizationConfidence; }
    public void setNormalizationConfidence(Double normalizationConfidence) { this.normalizationConfidence = normalizationConfidence; }

    public AppointmentEntity getAppointment() { return appointment; }
    public void setAppointment(AppointmentEntity appointment) { this.appointment = appointment; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }   // 👈 add this
    public void setMessage(String message) { this.message = message; }  // 👈 add this
}