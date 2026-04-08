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
        // Các lỗi OCR hay gặp với tiếng Việt (có thể mở rộng dần theo dữ liệu thực tế)
        COMMON_FIXES.put("CONG", "CÔNG");
        COMMON_FIXES.put(" HOA ", " HÒA ");
        COMMON_FIXES.put("HOA", "HÒA");
    }

    private TextPostProcessor() {
    }

    public static String postProcess(String raw) {
        if (raw == null) {
            return "";
        }

        // 1) Chuẩn hóa Unicode tiếng Việt (NFC giúp dấu kết hợp ổn định hơn)
        String text = Normalizer.normalize(raw, Normalizer.Form.NFC);

        // 2) Loại bỏ ký tự điều khiển lạ / ký tự rác
        text = CONTROL_CHARS.matcher(text).replaceAll("");
        text = JUNK.matcher(text).replaceAll(" ");

        // 3) Sửa một số lỗi phổ biến
        for (Map.Entry<String, String> e : COMMON_FIXES.entrySet()) {
            text = text.replace(e.getKey(), e.getValue());
        }

        // 4) Normalize whitespace
        text = text.replace('\u00A0', ' '); // NBSP
        text = MULTI_SPACE.matcher(text).replaceAll(" ");
        text = MULTI_NEWLINE.matcher(text).replaceAll("\n\n");

        return text.trim();
    }
}

