package com.healthcare.ai_appointmentscheduler.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ExtractedEntities {
    @JsonProperty("date_phrase")
    private String datePhrase;

    @JsonProperty("time_phrase")
    private String timePhrase;

    private String department;

    public String getDatePhrase() { return datePhrase; }
    public void setDatePhrase(String datePhrase) { this.datePhrase = datePhrase; }

    public String getTimePhrase() { return timePhrase; }
    public void setTimePhrase(String timePhrase) { this.timePhrase = timePhrase; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
}