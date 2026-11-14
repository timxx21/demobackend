package com.example.demo.service;

import com.example.demo.dto.InboxEvent;
import com.example.demo.dto.OutboxEvent;
import com.example.demo.repository.InboxRepository;
import com.example.demo.repository.OutboxRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OutboxRelay {
    private final OutboxRepository outboxRepository;
    private final InboxRepository inboxRepository;
    private final StringRedisTemplate redisTemplate; // Наш инструмент работы с Redis
    private final HealthCheckService healthCheckService;


    public OutboxRelay(OutboxRepository outboxRepository,
                       InboxRepository inboxRepository,
                       StringRedisTemplate redisTemplate,
                       HealthCheckService healthCheckService) {
        this.outboxRepository = outboxRepository;
        this.inboxRepository = inboxRepository;
        this.redisTemplate = redisTemplate;
        this.healthCheckService = healthCheckService;

    }

    @Scheduled(fixedDelay = 5000) // Метод запускается автоматически каждые 5 секунд!
    public void processOutbox() {
        // 1. Вытаскиваем из СУБД задачи, готовые к отправке
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatus("QUEUED");

        if (pendingEvents.isEmpty()) return;

        System.out.println("=== OutboxRelay: Найдено событий для отправки: " + pendingEvents.size() + " ===");

        for (OutboxEvent event : pendingEvents) {

            // 2. БРОКЕР СООБЩЕНИЙ: Пушим строки в Redis по одной, как вы переписали
            try {
                for (String url : event.getUrls()) {
                    redisTemplate.opsForList().leftPush("task-queue", url);
                }
                System.out.println("[REDIS] Пакет URL успешно опубликован в task-queue");
            } catch (Exception e) {
                System.err.println("[REDIS ERROR] Брокер недоступен: " + e.getMessage());
                continue; // Если Redis лежит, не меняем статус в БД и попробуем на следующем тике
            }

            // 3. TRANSACTIONAL INBOX: Защита от повторной обработки (идемпотентность)
            boolean isUnique = saveToInbox(event.getId());

            if (!isUnique) {
                System.out.println("[TRANSACTIONAL INBOX] Дубль события " + event.getId() + ". Пропускаем пинг.");
                updateEventStatus(event, "PROCESSED");
                continue;
            }

            // 4. CONSUMER: Реальная бизнес-логика асинхронного пинга
            System.out.println("[CONSUMER] Поток взял в работу задачу: " + event.getTaskId());

            for (String url : event.getUrls()) {
                System.out.println("[CONSUMER] HTTP-пинг: " + url);
                int httpStatus = healthCheckService.pingUrl(url);
                System.out.println("[CONSUMER] Результат для " + url + " -> HTTP " + httpStatus);
            }

            // 5. Финализируем статус всей пачки в PostgreSQL
            updateEventStatus(event, "PROCESSED");
        }
    }

    @Transactional
    public void updateEventStatus(OutboxEvent event, String status) {
        event.setStatus(status);
        outboxRepository.save(event);
    }


    // Изолируем трансляцию в Inbox, чтобы ошибка дубликата ключа (Unique Constraint) не рушила весь цикл
    private boolean saveToInbox(UUID eventId) {
        try {
            inboxRepository.saveAndFlush(new InboxEvent(eventId));
            return true;
        } catch (Exception e) {
            return false;
        }

    }

}
