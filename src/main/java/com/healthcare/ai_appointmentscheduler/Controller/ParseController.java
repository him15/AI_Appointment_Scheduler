package com.healthcare.ai_appointmentscheduler.Controller;

import com.healthcare.ai_appointmentscheduler.dto.ParseResponse;
import com.healthcare.ai_appointmentscheduler.dto.TextParseRequest;
import com.healthcare.ai_appointmentscheduler.service.OcrServiceImpl;
import com.healthcare.ai_appointmentscheduler.service.PipelineService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("ai_task/parse")
public class ParseController {

    private final PipelineService pipelineService;
    private final OcrServiceImpl ocrService;

    public ParseController(PipelineService pipelineService, OcrServiceImpl ocrService) {
        this.pipelineService = pipelineService;
        this.ocrService = ocrService;
    }

    /**
     * Parse raw text input (JSON body).
     * Body example: { "text": "Book dentist next Friday at 3pm" }
     */
    @PostMapping("/text")
    public ResponseEntity<ParseResponse> parseText(@RequestBody TextParseRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            ParseResponse p = new ParseResponse();
            p.setRawText("");
            p.setStatus("needs_clarification");
            p.setMessage("Empty text provided");
            return ResponseEntity.badRequest().body(p);
        }
        ParseResponse resp = pipelineService.parseText(request.getText());
        return ResponseEntity.ok(resp);
    }

    /**
     * Parse uploaded image (form-data key "file").
     * Returns the pipeline ParseResponse (OCR -> pipeline).
     */
    @PostMapping("/image")
    public ResponseEntity<?> parseImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file uploaded or file is empty"));
        }

        try {
            ParseResponse resp = ocrService.parseImageAndRunPipeline(file);
            return ResponseEntity.ok(resp);
        } catch (IOException ioe) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to read/process uploaded file", "details", ioe.getMessage()));
        } catch (Exception ex) {
            // catch-all to avoid leaking stack traces
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unexpected server error", "details", ex.getMessage()));
        }
    }
}