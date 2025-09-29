package com.healthcare.ai_appointmentscheduler.config;

import java.util.Arrays;
import java.util.List;

public class DepartmentConfig {
    // Controlled vocabulary for department/entity detection
    public static final List<String> DEPARTMENTS = Arrays.asList(
            "dentist",
            "cardiologist",
            "neurologist",
            "orthopedic",
            "dermatologist",
            "ent"
    );
}