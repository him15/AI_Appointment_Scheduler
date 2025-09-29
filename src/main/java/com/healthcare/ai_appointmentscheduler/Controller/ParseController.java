package com.healthcare.ai_appointmentscheduler.Controller;

import com.healthcare.ai_appointmentscheduler.dto.ParseResponse;
import com.healthcare.ai_appointmentscheduler.dto.TextParseRequest;
import com.healthcare.ai_appointmentscheduler.service.OcrServiceImpl;
import com.healthcare.ai_appointmentscheduler.service.PipelineService;
import net.sourceforge.tess4j.TesseractException;
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
     * Parse plain text input.
     */
    @PostMapping("/text")
    public ResponseEntity<ParseResponse> parseText(@RequestBody TextParseRequest request) {
        String text = (request != null) ? request.getText() : null;
        ParseResponse response = pipelineService.parseText(text);
        return ResponseEntity.ok(response);
    }

    /**
     * Parse uploaded image by running OCR -> preprocessing -> entity extraction -> normalization.
     */
    @PostMapping("/image")
    public ResponseEntity<?> parseImage(@RequestParam("file") MultipartFile file) {
        try {
            // Let OcrServiceImpl handle both OCR and pipeline
            ParseResponse response = ocrService.parseImageAndRunPipeline(file);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (IOException ioe) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process uploaded file", "details", ioe.getMessage()));
        } catch (TesseractException te) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "OCR engine error", "details", te.getMessage()));
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unexpected error", "details", ex.getMessage()));
        }
    }
}