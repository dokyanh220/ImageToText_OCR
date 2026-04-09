package com.itt.backend.service;

import lombok.RequiredArgsConstructor;
import com.itt.backend.util.ImagePreprocessor;
import com.itt.backend.util.TextPostProcessor;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IttService {

    private static final Logger log = LoggerFactory.getLogger(IttService.class);
    private final ImagePreprocessor imagePreprocessor;

    public String readText(MultipartFile file) {
        // Giữ hành vi cũ: gọi FAST để không phá code hiện tại
        return readText(file, OcrMode.FAST);
    }

    public String readText(MultipartFile file, OcrMode mode) {
        File temp = null;
        List<File> processedVariants = null;
        try {
            String originalName = file.getOriginalFilename();
            String ext = ".png";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }

            temp = File.createTempFile("ocr_", ext);
            file.transferTo(temp);

            log.info("OCR Unified Pipeline: originalName={}", originalName);

            // 1) Tiền xử lý ảnh (Unified high-fidelity pipeline)
            processedVariants = imagePreprocessor.preprocess(temp, mode);

            // 2) Multi-pass OCR: Thử nhiều PSM và chọn kết quả có điểm số cao nhất
            String bestText = null;
            int bestScore = Integer.MIN_VALUE;

            // Bộ PSM chiến lược: 1 (Auto+OSD), 3 (Fully Auto), 6 (Single Block), 11 (Sparse Text)
            int[] psms = { 1, 3, 6, 11 };

            for (File processed : processedVariants) {
                for (int psm : psms) {
                    try {
                        Tesseract tesseract = buildTesseract(psm);
                        String raw = tesseract.doOCR(processed);
                        String post = TextPostProcessor.postProcess(raw);

                        int score = scoreOcrText(post);
                        log.debug("OCR pass: psm={}, score={}, preview={}", 
                            psm, score, post.length() > 50 ? post.substring(0, 50) + "..." : post);

                        if (score > bestScore) {
                            bestScore = score;
                            bestText = post;
                        }
                    } catch (Exception e) {
                        log.warn("OCR pass failed for PSM {}", psm);
                    }
                }
            }

            return bestText == null ? "" : bestText;

        } catch (Exception e) {
            log.error("OCR Hệ thống lỗi", e);
            throw new RuntimeException("Lỗi xử lý OCR: " + e.getMessage(), e);
        } finally {
            if (temp != null && temp.exists()) temp.delete();
            if (processedVariants != null) {
                for (File f : processedVariants) {
                    if (f != null && f.exists()) f.delete();
                }
            }
        }
    }

    private Tesseract buildTesseract(int pageSegMode) {
        Tesseract tesseract = new Tesseract();

        // Tess4J: datapath phải trỏ tới thư mục chứa các file *.traineddata
        // (Windows mặc định: C:/Program Files/Tesseract-OCR/tessdata)
        tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata");
        tesseract.setLanguage("vie+eng");
        tesseract.setOcrEngineMode(1); // LSTM only
        tesseract.setPageSegMode(pageSegMode);

        tesseract.setVariable("user_defined_dpi", "300");
        tesseract.setVariable("preserve_interword_spaces", "1");

        return tesseract;
    }

    /**
     * Score heuristic để chọn kết quả tốt hơn:
     * - ưu tiên text dài vừa phải, nhiều chữ/có dấu, ít ký tự lạ.
     */
    private int scoreOcrText(String text) {
        if (text == null || text.isBlank()) return -10_000;

        int vietnameseChars = 0;
        int wordCount = text.split("\\s+").length;
        int weirdSymbols = 0;
        int upperCaseCount = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // Ký tự tiếng Việt có dấu (NFC)
            if ((c >= 0x0100 && c <= 0x024F) || (c >= 0x1E00 && c <= 0x1EFF) || (c >= 0x0300 && c <= 0x036F)) {
                vietnameseChars++;
            }
            // Ký tự lạ thường xuất hiện khi OCR lỗi
            if ("~`@#$%^&*()_+=[]{}|\\<>".indexOf(c) >= 0) {
                weirdSymbols++;
            }
            if (Character.isUpperCase(c)) {
                upperCaseCount++;
            }
        }

        // Heuristic scoring: Ưu tiên tiếng Việt, trừ điểm rác
        int score = (vietnameseChars * 4) + (wordCount * 2) - (weirdSymbols * 5);
        
        // Phạt nếu tỷ lệ chữ in hoa quá cao (thường là rác ở background)
        if (text.length() > 20 && (double)upperCaseCount / text.length() > 0.8) {
            score -= 50;
        }

        return score;
    }
}