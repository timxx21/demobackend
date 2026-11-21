package com.example.demo.dto;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "check_results")
@Data
public class CheckResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID taskId;

    @Column(nullable = false)
    private String url;

    private int statusCode;
    private boolean isAvailable;
    private LocalDateTime checkedAt = LocalDateTime.now();
}
