package com.healthcare.ai_appointmentscheduler.service;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class TextPreprocessorImpl {

    public String preprocess(String raw) {
        if (raw == null) return "";
        // just lowercase + normalize spaces
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return s;
    }
}