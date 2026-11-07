package com.example.demo.dto;

import jakarta.persistence.*;
import lombok.Data;
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

    @Column(columnDefinition = "TEXT")
    private String payload; // Сюда завернем JSON со списком URL

    private String status = "PENDING"; // По умолчанию задача ждет отправки
}

