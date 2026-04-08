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

            log.debug("OCR: mode={}, originalName={}, size={}", mode, originalName, file.getSize());
            log.debug("OCR: before preprocess temp={}", temp.getAbsolutePath());

            // 1) Tiền xử lý ảnh (FAST: 1 ảnh, ACCURATE: có thể nhiều biến thể normal/inverted)
            processedVariants = imagePreprocessor.preprocess(temp, mode);
            for (int i = 0; i < processedVariants.size(); i++) {
                log.debug("OCR: after preprocess variant[{}]={}", i, processedVariants.get(i).getAbsolutePath());
            }

            // 2) OCR: FAST = 1 pass; ACCURATE = multi-pass (PSM 6 + PSM 4) và chọn kết quả tốt hơn
            String bestRaw = null;
            int bestScore = Integer.MIN_VALUE;

            int[] psms = (mode == OcrMode.ACCURATE) ? new int[] { 6, 4 } : new int[] { 3 };

            for (int v = 0; v < processedVariants.size(); v++) {
                File processed = processedVariants.get(v);
                for (int psm : psms) {
                    Tesseract tesseract = buildTesseract(psm);
                    String raw = tesseract.doOCR(processed);
                    String post = TextPostProcessor.postProcess(raw);

                    int score = scoreOcrText(post);
                    log.debug("OCR: variant={}, psm={}, rawLen={}, postLen={}, score={}, preview={}",
                            v, psm,
                            raw == null ? 0 : raw.length(),
                            post.length(),
                            score,
                            post.length() > 80 ? post.substring(0, 80) + "..." : post);

                    if (score > bestScore) {
                        bestScore = score;
                        bestRaw = post;
                    }
                }
            }

            return bestRaw == null ? "" : bestRaw;

        } catch (Exception e) {
            log.error("OCR lỗi", e);
            throw new RuntimeException("OCR lỗi: " + e.getMessage(), e);
        } finally {
            // Dọn dẹp file tạm
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

        int letters = 0;
        int digits = 0;
        int spaces = 0;
        int diacritics = 0;
        int weird = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) letters++;
            else if (Character.isDigit(c)) digits++;
            else if (Character.isWhitespace(c)) spaces++;
            else if ("_`~^|".indexOf(c) >= 0) weird++;

            // heuristic dấu tiếng Việt: các ký tự Latin mở rộng & combining marks
            if (c >= 0x0300 && c <= 0x036F) diacritics++;
            if (c >= 0x0100 && c <= 0x1EFF) diacritics++;
        }

        int len = text.length();
        int compactLen = len - spaces;

        // chiều dài quá ngắn thường là fail; quá dài có thể là noise nhưng vẫn có ích
        int base = Math.min(compactLen, 1200);
        int score = base + letters * 3 + digits - weird * 10 + diacritics * 2;

        // phạt nếu nhiều ký tự không phải chữ/số
        int nonAlnum = compactLen - letters - digits;
        score -= Math.max(0, nonAlnum) * 2;

        return score;
    }
}