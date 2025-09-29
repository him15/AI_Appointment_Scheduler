package com.healthcare.ai_appointmentscheduler.Controller;

import com.healthcare.ai_appointmentscheduler.dto.ParseResponse;
import com.healthcare.ai_appointmentscheduler.dto.TextParseRequest;
import com.healthcare.ai_appointmentscheduler.service.PipelineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("ai_task/parse")
public class ParseController {

    private final PipelineService pipelineService;

    public ParseController(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @PostMapping("/text")
    public ResponseEntity<ParseResponse> parseText(@RequestBody TextParseRequest request) {
        ParseResponse response = pipelineService.parseText(request.getText());
        return ResponseEntity.ok(response);
    }
}