package com.healthcare.ai_appointmentscheduler.service;

import com.healthcare.ai_appointmentscheduler.dto.ParseResponse;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * OCR service using Tess4J. Provides:
 *  - extractText(MultipartFile) -> raw OCR string
 *  - parseImageAndRunPipeline(MultipartFile) -> runs OCR then calls PipelineService.parseText(...)
 *
 * Notes:
 *  - Ensure native libs are reachable (java.library.path / jna.library.path) if using Tess4J native bindings.
 *  - You can override tessdata path via application.properties: ocr.tessdata.path
 */
@Service
public class OcrServiceImpl {

    // configurable; overridden by application.properties where appropriate
    @Value("${ocr.tessdata.path:/usr/local/share/tessdata}")
    private String tessdataPath;

    @Value("${ocr.language:eng}")
    private String tessLanguage;

    private final PipelineService pipelineService;

    public OcrServiceImpl(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    /** High-level helper: extract text from image and run the main pipeline */
    public ParseResponse parseImageAndRunPipeline(MultipartFile file) throws IOException, TesseractException {
        String extracted = extractText(file);
        return pipelineService.parseText(extracted);
    }

    /** Extract text from the uploaded image file using Tess4J, with CLI fallback */
    public String extractText(MultipartFile file) throws IOException, TesseractException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        // build temp file (preserve extension when possible)
        String original = Objects.requireNonNullElse(file.getOriginalFilename(), "upload");
        String suffix = original.contains(".") ? original.substring(original.lastIndexOf('.')) : ".png";
        File tempFile = File.createTempFile("ocr-input-", suffix);

        try {
            file.transferTo(tempFile);

            ITesseract tesseract = new Tesseract();
            String datapath = resolveTessdataPath();
            if (datapath != null) {
                tesseract.setDatapath(datapath);
            }
            tesseract.setLanguage(tessLanguage);

            // try Tess4J
            return safeTrim(tesseract.doOCR(tempFile));

        } catch (UnsatisfiedLinkError ule) {
            // fallback to CLI if Tess4J native libs missing
            return extractTextUsingCli(tempFile);

        } finally {
            // cleanup temp file
            if (tempFile.exists()) tempFile.delete();
        }
    }

    /** Resolve tessdata path (config first, then common locations) */
    private String resolveTessdataPath() {
        if (tessdataPath != null && !tessdataPath.isBlank()) {
            File f = new File(tessdataPath);
            if (f.exists() && f.isDirectory()) return tessdataPath;
        }

        String[] candidates = {
                "/opt/homebrew/share/tessdata", // Apple Silicon
                "/usr/local/share/tessdata",    // Intel Macs / Homebrew
                "/usr/share/tessdata",          // Linux
                "/usr/share/local/tessdata"
        };

        for (String c : candidates) {
            File f = new File(c);
            if (f.exists() && f.isDirectory()) return c;
        }

        return tessdataPath; // may still fail but explicit
    }

    /** CLI fallback: calls `tesseract <image> stdout` */
    private String extractTextUsingCli(File imageFile) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("tesseract", imageFile.getAbsolutePath(), "stdout", "-l", tessLanguage);
        Process process = pb.start();
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append(" ");
            }
            return safeTrim(sb.toString());
        } catch (Exception e) {
            throw new IOException("CLI OCR extraction failed", e);
        }
    }

    private String safeTrim(String raw) {
        return raw == null ? "" : raw.trim();
    }
}