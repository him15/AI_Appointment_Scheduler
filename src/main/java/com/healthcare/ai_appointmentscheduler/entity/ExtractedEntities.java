package com.healthcare.ai_appointmentscheduler.entity;

public class ExtractedEntities {
    private String department;
    private String datePhrase;
    private String timePhrase;

    // --- NEW FIELD TO FIX THE ERROR ---
    // This field will store the confidence score (0.0 to 1.0) of the department match.
    private double departmentConfidence;

    // --- GETTERS AND SETTERS ---

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDatePhrase() {
        return datePhrase;
    }

    public void setDatePhrase(String datePhrase) {
        this.datePhrase = datePhrase;
    }

    public String getTimePhrase() {
        return timePhrase;
    }

    public void setTimePhrase(String timePhrase) {
        this.timePhrase = timePhrase;
    }

    // --- GETTER AND SETTER FOR THE NEW FIELD ---

    public double getDepartmentConfidence() {
        return departmentConfidence;
    }

    public void setDepartmentConfidence(double departmentConfidence) {
        this.departmentConfidence = departmentConfidence;
    }
}