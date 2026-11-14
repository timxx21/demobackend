package com.example.demo.dto;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inbox_events")
@Data
@NoArgsConstructor
public class InboxEvent {
    @Id
    private UUID id; // ID из таблицы outbox_events

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt = LocalDateTime.now();

    public InboxEvent(UUID id) {
        this.id = id;
    }
}

