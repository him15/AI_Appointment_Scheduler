package com.healthcare.ai_appointmentscheduler.service;

import com.healthcare.ai_appointmentscheduler.dto.ParseResponse;

public interface PipelineService {
    ParseResponse parseText(String text);
}