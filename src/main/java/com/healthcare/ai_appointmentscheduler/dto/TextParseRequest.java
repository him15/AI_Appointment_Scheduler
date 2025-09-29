package com.healthcare.ai_appointmentscheduler.dto;

public class TextParseRequest {
    private String text;

    public TextParseRequest() {}
    public TextParseRequest(String text) { this.text = text; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}