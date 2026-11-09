package com.example.demo.service;

import com.example.demo.dto.OutboxEvent;
import com.example.demo.dto.InboxEvent;
import com.example.demo.repository.OutboxRepository;
import com.example.demo.repository.InboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final InboxRepository inboxRepository; // Добавлено
    private final StringRedisTemplate redisTemplate;
    private final HealthCheckService healthCheckService; // Добавлено

    // Обновленный конструктор
    public OutboxRelay(OutboxRepository outboxRepository,
                       InboxRepository inboxRepository,
                       StringRedisTemplate redisTemplate,
                       HealthCheckService healthCheckService) {
        this.outboxRepository = outboxRepository;
        this.inboxRepository = inboxRepository;
        this.redisTemplate = redisTemplate;
        this.healthCheckService = healthCheckService;
    }

    @Scheduled(fixedDelay = 5000)
    public void processOutbox() {
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatus("QUEUED");

        if (pendingEvents.isEmpty()) return;

        log.info("=== OutboxRelay: Найдено пакетов для обработки: {} ===", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            // 1. БРОКЕР СООБЩЕНИЙ: Пушим в Redis
            try {
                for (String url : event.getUrls()) {
                    redisTemplate.opsForList().leftPush("task-queue", url);
                }
                log.info("[REDIS] Пакет URL успешно опубликован в task-queue для события {}", event.getId());
            } catch (Exception e) {
                log.error("[REDIS ERROR] Брокер недоступен: {}", e.getMessage());
                continue; // Если Redis лежит, не меняем статус и попробуем на следующем тике
            }

            // 2. TRANSACTIONAL INBOX: Защита от повторной обработки
            // ВНИМАНИЕ: Если у тебя ID типа UUID, замени Long на UUID в аргументах метода saveToInbox и в классе InboxEvent
            boolean isUnique = saveToInbox(event.getId());

            if (!isUnique) {
                log.info("[TRANSACTIONAL INBOX] Дубль события {}. Пропускаем пинг.", event.getId());
                updateEventStatus(event, "PROCESSED");
                continue;
            }

            // 3. CONSUMER: Реальная бизнес-логика асинхронного пинга
            log.info("[CONSUMER] Поток взял в работу задачу: {}", event.getTaskId());

            for (String url : event.getUrls()) {
                log.info("[CONSUMER] HTTP-пинг: {}", url);
                int httpStatus = healthCheckService.pingUrl(url); // Убедись, что в HealthCheckService метод назван pingUrl с большой U!
                log.info("[CONSUMER] Результат для {} -> HTTP {}", url, httpStatus);
            }

            // 4. Финализируем статус всей пачки в PostgreSQL (ВНЕ ЦИКЛА URL!)
            updateEventStatus(event, "PROCESSED");
        }
    }

    private boolean saveToInbox(UUID eventId) { // Если ID = UUID, поменяй тип на UUID
        try {
            inboxRepository.saveAndFlush(new InboxEvent(eventId));
            return true;
        } catch (Exception _) {
            return false; // Ловим ошибку уникальности (дубль)
        }
    }

    @Transactional
    public void updateEventStatus(OutboxEvent event, String status) {
        event.setStatus(status);
        outboxRepository.save(event);
    }
}