package com.itt.backend.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PdfExportService {

    public byte[] exportToPdf(String text) {
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Load font hỗ trợ tiếng Việt (Arial từ Windows)
            BaseFont bf;
            try {
                bf = BaseFont.createFont("C:\\Windows\\Fonts\\arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                log.warn("Không tìm thấy font Arial, fallback sang font mặc định (có thể lỗi tiếng Việt)");
                bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            }
            
            Font font = new Font(bf, 12);
            
            // Xử lý xuống dòng trong text
            String[] lines = text.split("\n");
            for (String line : lines) {
                if (line.isBlank()) {
                    document.add(new Paragraph(" ", font));
                } else {
                    document.add(new Paragraph(line, font));
                }
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Lỗi khi tạo PDF", e);
            throw new RuntimeException("Không thể tạo file PDF: " + e.getMessage());
        }
    }
}
