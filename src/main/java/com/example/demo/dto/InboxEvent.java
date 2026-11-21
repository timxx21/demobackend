package com.example.demo.dto;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "inbox_events")
@Data
@NoArgsConstructor
public class InboxEvent {
    @Id
    private String messageKey; // Вида "uuidСобытия_https://ya.ru"

    private LocalDateTime processedAt = LocalDateTime.now();

    public InboxEvent(String messageKey) {
        this.messageKey = messageKey;
    }
}