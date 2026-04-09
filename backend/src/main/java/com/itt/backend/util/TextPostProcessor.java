package com.itt.backend.util;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class TextPostProcessor {
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cc}\\p{Cf}&&[^\\r\\n\\t]]+");
    private static final Pattern JUNK = Pattern.compile("[^\\p{L}\\p{N}\\p{P}\\p{Zs}\\r\\n\\t]+");
    private static final Pattern MULTI_SPACE = Pattern.compile("[\\p{Zs}\\t\\x0B\\f]+");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("(\\r?\\n){3,}");

    private static final Map<String, String> COMMON_FIXES = new LinkedHashMap<>();

    static {
        // Các lỗi OCR hay gặp với tiếng Việt (tự động thêm dấu cho các từ cực kỳ phổ biến)
        COMMON_FIXES.put("van ban", "văn bản");
        COMMON_FIXES.put("de dang", "dễ dàng");
        COMMON_FIXES.put("nhanh chong", "nhanh chóng");
        COMMON_FIXES.put("Chuyen doi", "Chuyển đổi");
        COMMON_FIXES.put("hinh anh", "hình ảnh");
        COMMON_FIXES.put("nguoi", "người");
        COMMON_FIXES.put("khong", "không");
        COMMON_FIXES.put("duoc", "được");
        COMMON_FIXES.put("tieng", "tiếng");
        COMMON_FIXES.put("Viet", "Việt");
        
        // Khắc phục lỗi thiếu chữ 'i' trong 'đổi'
        COMMON_FIXES.put("chuyển đổ ", "chuyển đổi ");
        COMMON_FIXES.put("Chuyển đổ ", "Chuyển đổi ");
        
        // Sửa lỗi dấu hỏi/ngã hay sai
        COMMON_FIXES.put(" hổ trợ", " hỗ trợ");
        COMMON_FIXES.put(" sử dung", " sử dụng");
    }

    private TextPostProcessor() {
    }

    public static String postProcess(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String text = raw;
        // Áp dụng các fix phổ biến TRƯỚC khi xử lý khác
        for (Map.Entry<String, String> entry : COMMON_FIXES.entrySet()) {
            text = text.replaceAll("(?i)" + Pattern.quote(entry.getKey()), entry.getValue());
        }

        // 1) Chuẩn hóa Unicode tiếng Việt (NFC help diacritics be stable)
        text = Normalizer.normalize(text, Normalizer.Form.NFC);

        // 2) Loại bỏ ký tự điều khiển lạ / ký tự rác
        // Chỉ giữ lại chữ, số, khoảng trắng và các dấu câu cơ bản
        text = text.replaceAll("[^\\p{L}\\p{N}\\s.,:;!?()-/]", " ");

        // 3) Normalize whitespace
        text = text.replace('\u00A0', ' '); // NBSP
        text = MULTI_SPACE.matcher(text).replaceAll(" ");
        text = MULTI_NEWLINE.matcher(text).replaceAll("\n\n");

        return text.trim();
    }
}

