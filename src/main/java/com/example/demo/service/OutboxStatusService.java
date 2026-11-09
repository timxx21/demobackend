package com.example.demo.service;

import com.example.demo.dto.OutboxEvent;
import com.example.demo.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxStatusService {

    private final OutboxRepository outboxRepository;

    @Transactional
    public void updateStatus(OutboxEvent event, String status) {
        event.setStatus(status);
        outboxRepository.save(event);
        log.debug("Статус события {} изменен на {}", event.getId(), status);
    }
}