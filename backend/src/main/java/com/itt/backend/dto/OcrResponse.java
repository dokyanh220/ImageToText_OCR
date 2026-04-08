package com.itt.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OcrResponse {
    private String text;
    private String fileName;
}