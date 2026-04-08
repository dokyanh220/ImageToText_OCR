package com.itt.backend.util;

import org.springframework.web.multipart.MultipartFile;

public class FileValidator {

    public static boolean isValidImage(MultipartFile file) {
        String type = file.getContentType();
        String name = file.getOriginalFilename();

        return type != null &&
                (type.equals("image/png") || type.equals("image/jpeg")) &&
                name != null &&
                (name.endsWith(".png") ||
                        name.endsWith(".jpg") ||
                        name.endsWith(".jpeg"));
    }
}