package com.healthcare.ai_appointmentscheduler.service;

import com.healthcare.ai_appointmentscheduler.dto.ParseResponse;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

@Service
public class OcrServiceImpl {

    @Value("${ocr.tessdata.path:/opt/homebrew/share/tessdata}")
    private String tessdataPath;

    @Value("${ocr.language:eng}")
    private String tessLanguage;

    private final PipelineService pipelineService;

    public OcrServiceImpl(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    /**
     * High-level entry for OCR + pipeline
     */
    public ParseResponse parseImageAndRunPipeline(MultipartFile file) throws IOException, TesseractException {
        String extracted = extractText(file);
        return pipelineService.parseText(extracted);
    }

    /**
     * OCR with preprocessing improvements (rotation, scale, grayscale, binarization)
     */
    public String extractText(MultipartFile file) throws IOException, TesseractException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        // Save file temporarily
        String original = file.getOriginalFilename() == null ? "upload.png" : file.getOriginalFilename();
        String suffix = original.contains(".") ? original.substring(original.lastIndexOf('.')) : ".png";
        File tempFile = File.createTempFile("ocr-input-", suffix);
        file.transferTo(tempFile);

        try {
            BufferedImage img = ImageIO.read(tempFile);
            if (img == null) {
                throw new IOException("Invalid image file");
            }

            // Preprocess: scale up → grayscale → binarize
            img = resize(img, img.getWidth() * 2, img.getHeight() * 2);
            img = toGrayscale(img);
            img = otsuThreshold(img);

            File processed = File.createTempFile("ocr-processed-", ".png");
            ImageIO.write(img, "png", processed);

            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath(resolveTessdataPath());
            tesseract.setLanguage(tessLanguage);

            // 🔑 Improve OCR for structured handwritten/typed text
            tesseract.setPageSegMode(6); // assume single block of text
            tesseract.setOcrEngineMode(1); // LSTM only
            tesseract.setVariable("tessedit_char_whitelist",
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789:. ");

            String raw = tesseract.doOCR(processed);
            return raw == null ? "" : raw.trim();

        } finally {
            if (tempFile.exists()) tempFile.delete();
        }
    }

    // -------- Helper methods for preprocessing --------

    private String resolveTessdataPath() {
        File f = new File(tessdataPath);
        if (f.exists() && f.isDirectory()) return tessdataPath;
        return "/opt/homebrew/share/tessdata"; // fallback for Mac Homebrew
    }

    private BufferedImage resize(BufferedImage src, int newW, int newH) {
        Image tmp = src.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, newW, newH);
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return resized;
    }

    private BufferedImage toGrayscale(BufferedImage src) {
        BufferedImage gray = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = gray.getGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return gray;
    }

    private BufferedImage otsuThreshold(BufferedImage gray) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        Raster raster = gray.getData();
        int[] hist = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                hist[raster.getSample(x, y, 0)]++;
            }
        }

        int total = w * h;
        float sum = 0;
        for (int i = 0; i < 256; i++) sum += i * hist[i];

        float sumB = 0;
        int wB = 0, wF;
        float varMax = 0;
        int threshold = 0;

        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            wF = total - wB;
            if (wF == 0) break;
            sumB += (float) (t * hist[t]);
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;
            float varBetween = (float) wB * wF * (mB - mF) * (mB - mF);
            if (varBetween > varMax) {
                varMax = varBetween;
                threshold = t;
            }
        }

        BufferedImage binary = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        WritableRaster wr = binary.getRaster();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                wr.setSample(x, y, 0, raster.getSample(x, y, 0) > threshold ? 255 : 0);
            }
        }
        return binary;
    }
}