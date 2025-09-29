package com.healthcare.ai_appointmentscheduler.entity;

public class AppointmentEntity {
    private String department;
    private String date;
    private String time;
    private String tz;

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getTz() { return tz; }
    public void setTz(String tz) { this.tz = tz; }
}