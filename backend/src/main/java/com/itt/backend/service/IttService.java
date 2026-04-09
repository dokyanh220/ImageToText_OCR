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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;

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
            String contentType = file.getContentType();
            
            log.info("OCR Pipeline Started: originalName={}, contentType={}, mode={}", originalName, contentType, mode);

            if (contentType != null && contentType.equalsIgnoreCase("application/pdf")) {
                return readTextFromPdf(file, mode);
            }

            String ext = ".png";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }

            temp = File.createTempFile("ocr_", ext);
            file.transferTo(temp);

            // 1) Tiền xử lý ảnh (Unified high-fidelity pipeline)
            processedVariants = imagePreprocessor.preprocess(temp, mode);

            // 2) Multi-pass OCR
            return performMultiPassOcr(processedVariants);
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

    private String readTextFromPdf(MultipartFile file, OcrMode mode) throws Exception {
        StringBuilder fullText = new StringBuilder();
        try (PDDocument document = PDDocument.load(file.getBytes())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            
            log.info("Processing PDF: {} pages", pageCount);
            
            // Giới hạn xử lý tối đa 20 trang để tránh timeout/quá tải
            int maxPages = Math.min(pageCount, 20);

            for (int i = 0; i < maxPages; i++) {
                log.info("Processing Page {}/{}", i + 1, maxPages);
                BufferedImage bim = pdfRenderer.renderImageWithDPI(i, 300, ImageType.RGB);
                
                File tempPage = File.createTempFile("pdf_page_" + i + "_", ".png");
                try {
                    ImageIO.write(bim, "png", tempPage);
                    
                    // Run unified pipeline on this page
                    List<File> variants = imagePreprocessor.preprocess(tempPage, mode);
                    String pageText = performMultiPassOcr(variants);
                    
                    if (!pageText.isBlank()) {
                        fullText.append("--- Trang ").append(i + 1).append(" ---\n");
                        fullText.append(pageText).append("\n\n");
                    }
                    
                    // Cleanup variants
                    for (File v : variants) if (v != null && v.exists()) v.delete();
                } finally {
                    if (tempPage != null && tempPage.exists()) tempPage.delete();
                }
            }
        }
        return fullText.toString();
    }

    private String performMultiPassOcr(List<File> processedVariants) {
        String bestText = null;
        int bestScore = Integer.MIN_VALUE;
        int[] psms = { 1, 3, 6, 11 };

        for (File processed : processedVariants) {
            for (int psm : psms) {
                try {
                    Tesseract tesseract = buildTesseract(psm);
                    String raw = tesseract.doOCR(processed);
                    String post = TextPostProcessor.postProcess(raw);

                    int score = scoreOcrText(post);
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
        int commonWordScore = 0;
        int wordCount = text.split("\\s+").length;
        int weirdSymbols = 0;
        int upperCaseCount = 0;

        // Các từ tiếng Việt cực kỳ phổ biến để nhận diện độ "thật" của kết quả
        String[] commonSyllables = {"anh", "em", "cho", "người", "không", "có", "được", "ngữ", "văn", "hình", "ảnh", "chuyển", "đổi", "dễ", "dàng", "nhanh", "chóng"};

        for (String syllable : commonSyllables) {
            if (text.toLowerCase().contains(syllable)) commonWordScore += 20;
        }

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // Ký tự tiếng Việt có dấu (Dải Unicode mở rộng)
            if ((c >= 0x0100 && c <= 0x024F) || (c >= 0x1E00 && c <= 0x1EFF) || (c >= 0x0300 && c <= 0x036F)) {
                vietnameseChars++;
            }
            // Ký tự lạ thường xuất hiện khi OCR lỗi (noise)
            if ("~`@#$%^&*()_+=[]{}|\\<>".indexOf(c) >= 0) {
                weirdSymbols++;
            }
            if (Character.isUpperCase(c)) {
                upperCaseCount++;
            }
        }

        // Heuristic nâng cao: tỷ lệ ký tự tiếng Việt chiếm ưu thế
        int score = (vietnameseChars * 10) + (wordCount * 5) + commonWordScore - (weirdSymbols * 50);
        
        // Phạt nặng nếu text quá ngắn hoặc tỷ lệ chữ in hoa bất thường (banner rác)
        if (text.length() < 3) score -= 1000;
        if (text.length() > 20 && (double)upperCaseCount / text.length() > 0.8) {
            score -= 200;
        }

        return score;
    }
}