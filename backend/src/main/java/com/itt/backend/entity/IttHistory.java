package com.itt.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class IttHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;

    @Column(columnDefinition = "LONGTEXT")
    private String extractedText;

    private LocalDateTime createdAt;
}