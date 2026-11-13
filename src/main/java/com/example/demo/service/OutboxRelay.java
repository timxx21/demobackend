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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final InboxRepository inboxRepository; // Добавлено
    private final StringRedisTemplate redisTemplate;
    private final HealthCheckService healthCheckService; // Добавлено
    private final OutboxStatusService outboxStatusService;
    // Создаем пул из 10 потоков ("грузчиков"), которые будут делать пинги
    private final ExecutorService pingExecutor = Executors.newFixedThreadPool(10);
    // Обновленный конструктор
    public OutboxRelay(OutboxRepository outboxRepository,
                       InboxRepository inboxRepository,
                       StringRedisTemplate redisTemplate,
                       HealthCheckService healthCheckService,
                       OutboxStatusService outboxStatusService) {
        this.outboxRepository = outboxRepository;
        this.inboxRepository = inboxRepository;
        this.redisTemplate = redisTemplate;
        this.healthCheckService = healthCheckService;
        this.outboxStatusService = outboxStatusService;
    }

    @Scheduled(fixedDelay = 5000)
    public void processOutbox() {
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatus("QUEUED");
        if (pendingEvents.isEmpty()) return;

        log.info("=== OutboxRelay: Найдено пакетов для обработки: {} ===", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            // 1. Пушим в Redis
            try {
                for (String url : event.getUrls()) {
                    redisTemplate.opsForList().leftPush("task-queue", url);
                }
                log.info("[REDIS] Пакет URL успешно опубликован в task-queue для события {}", event.getId());
            } catch (Exception e) {
                log.error("[REDIS ERROR] Брокер недоступен: {}", e.getMessage());
                continue;
            }

            // 2. Проверяем Inbox (защита от дублей)
            boolean isUnique = saveToInbox(event.getId());
            if (!isUnique) {
                log.info("[TRANSACTIONAL INBOX] Дубль события {}. Пропускаем пинг.", event.getId());
                outboxStatusService.updateStatus(event, "PROCESSED");
                continue;
            }

            // 3. Пингуем URL-ы АСИНХРОННО
            log.info("[CONSUMER] Поток взял в работу задачу: {}", event.getTaskId());

            // Список для сбора результатов из разных потоков
            List<CompletableFuture<Integer>> futures = new java.util.ArrayList<>();

            for (String url : event.getUrls()) {
                CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                    log.info("[ASYNC] HTTP-пинг: {}", url);
                    int httpStatus = healthCheckService.pingUrl(url);
                    log.info("[ASYNC] Результат для {} -> HTTP {}", url, httpStatus);
                    return httpStatus;
                }, pingExecutor);

                futures.add(future);
            }

            // Ждем, пока ВСЕ пинги этой пачки завершатся, и собираем результаты
            List<Integer> results = futures.stream()
                    .map(CompletableFuture::join) // join() блокирует поток, но только для сбора результатов
                    .toList();

            // Проверяем, был ли хоть один провал
            boolean isAnyPingFailed = false;
            String failedUrl = "";
            for (int i = 0; i < results.size(); i++) {
                if (results.get(i) == 0) {
                    isAnyPingFailed = true;
                    failedUrl = event.getUrls().get(i); // Запоминаем, какой именно URL упал
                }
            }

            // 4. ЛОГИКА DEAD LETTER QUEUE И RETRY
            if (isAnyPingFailed) {
                event.setRetryCount(event.getRetryCount() + 1);
                event.setErrorMessage("Timeout or connection refused for " + failedUrl);

                if (event.getRetryCount() >= 3) {
                    log.error("[DLQ] Задача {} провалилась 3 раза. Отправляем в DLQ (статус FAILED).", event.getId());
                    outboxStatusService.updateStatus(event, "FAILED"); // Мертвая буква
                } else {
                    log.warn("[RETRY] Ошибка пинга. Попытка {}/3. Возвращаем задачу {}.", event.getRetryCount(), event.getId());
                    inboxRepository.deleteById(event.getId());
                    outboxStatusService.updateStatus(event, "QUEUED"); // Вернем в очередь на повтор
                }
            } else {
                // Все сайты ответили успешно (200, 301 и т.д.)
                outboxStatusService.updateStatus(event, "PROCESSED"); // Успех
            }
        }
    }

    private boolean saveToInbox(UUID eventId) {
        if (inboxRepository.existsById(eventId)) {
            return false; // Уже есть! Дубль.
        }
        try {
            inboxRepository.saveAndFlush(new InboxEvent(eventId));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public void updateEventStatus(OutboxEvent event, String status) {
        event.setStatus(status);
        outboxRepository.save(event);
    }
}