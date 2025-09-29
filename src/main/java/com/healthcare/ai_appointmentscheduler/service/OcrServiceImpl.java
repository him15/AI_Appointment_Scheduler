package com.healthcare.ai_appointmentscheduler.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Service
public class OcrServiceImpl {

    // configure this in application.properties or adjust here
    @Value("${ocr.tessdata.path:/usr/local/share/tessdata}")
    private String tessdataPath;

    @Value("${ocr.language:eng}")
    private String tessLanguage;

    /**
     * Extracts text from multipart image using Tess4J/Tesseract.
     * Throws IOException or TesseractException to caller for handling.
     */
    public String extractText(MultipartFile file) throws IOException, TesseractException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        // create temporary file with correct suffix from original filename (or .png fallback)
        String original = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String suffix = original.contains(".") ? original.substring(original.lastIndexOf('.')) : ".png";
        File temp = File.createTempFile("upload-", suffix);
        try {
            file.transferTo(temp); // write MultipartFile to disk

            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessdataPath); // point to tessdata folder
            tesseract.setLanguage(tessLanguage);

            // optional: tune to only detect lines (pageSegMode) or OCR engine mode
            // tesseract.setPageSegMode(1);

            String raw = tesseract.doOCR(temp);
            return raw == null ? "" : raw.trim();
        } finally {
            // best-effort cleanup
            try { if (temp.exists()) temp.delete(); } catch (Exception ignored) {}
        }
    }
}