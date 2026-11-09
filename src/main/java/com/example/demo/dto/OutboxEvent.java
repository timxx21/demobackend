package com.example.demo.dto;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Data

public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID taskId;
    private String eventType;


    private List<String> urls;
    private String status;

    private int retryCount = 0; // Сколько раз пытались
    private String errorMessage; // Текст последней ошибки
}

