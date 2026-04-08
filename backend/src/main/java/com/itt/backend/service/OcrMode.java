package com.itt.backend.service;

public enum OcrMode {
    FAST,
    ACCURATE;

    public static OcrMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return FAST;
        }
        return "accurate".equalsIgnoreCase(raw.trim()) ? ACCURATE : FAST;
    }
}

