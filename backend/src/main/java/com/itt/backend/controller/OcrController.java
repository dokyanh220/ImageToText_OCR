package com.itt.backend.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.itt.backend.dto.OcrResponse;
import com.itt.backend.service.IttService;
import com.itt.backend.service.OcrMode;
import com.itt.backend.service.PdfExportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
public class OcrController {
    private final IttService ittService;
    private final PdfExportService pdfExportService;

    @PostMapping
    public OcrResponse ocr(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", required = false) String mode) {
        OcrMode ocrMode = OcrMode.from(mode);
        String text = ittService.readText(file, ocrMode);
        return new OcrResponse(text, file.getOriginalFilename());
    }

    @PostMapping("/download") // download txt
    public ResponseEntity<byte[]> download(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", required = false) String mode) {
        OcrMode ocrMode = OcrMode.from(mode);
        String text = ittService.readText(file, ocrMode);

        String createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "ITT_" + createdAt + ".txt";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .body(text.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/download/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", required = false) String mode) {
        OcrMode ocrMode = OcrMode.from(mode);
        String text = ittService.readText(file, ocrMode);

        byte[] pdfBytes = pdfExportService.exportToPdf(text);

        String createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "ITT_" + createdAt + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .body(pdfBytes);
    }
}
