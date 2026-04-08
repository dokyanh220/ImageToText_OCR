package com.itt.backend.controller;

import com.itt.backend.service.IttService;
import com.itt.backend.util.FileValidator;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/itt")
@RequiredArgsConstructor
public class IttController {

    private final IttService ittService;

    @PostMapping("/ocr")
    public String ocr(@RequestParam("file") MultipartFile file) {

        if (!FileValidator.isValidImage(file)) {
            throw new RuntimeException("Chỉ hỗ trợ PNG/JPG");
        }

        return ittService.readText(file);
    }
}