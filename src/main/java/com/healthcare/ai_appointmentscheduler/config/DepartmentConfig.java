package com.healthcare.ai_appointmentscheduler.config;

import java.util.Arrays;
import java.util.Comparator; // <-- THIS IS THE FIX
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

    public static final List<String> DEPARTMENTS_SORTED_FOR_SEARCH;

    static {
        // This static block runs once when the class is loaded.
        // It creates a new list sorted by the length of the department names,
        // from longest to shortest, ensuring "cardiologist" is checked before "ent".
        DEPARTMENTS_SORTED_FOR_SEARCH = DEPARTMENTS.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }
}