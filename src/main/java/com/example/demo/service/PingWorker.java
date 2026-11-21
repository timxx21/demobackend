package com.example.demo.service;

import com.example.demo.dto.CheckResult;
import com.example.demo.dto.InboxEvent;
import com.example.demo.repository.CheckResultRepository;
import com.example.demo.repository.InboxRepository;
import com.example.demo.repository.TaskRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PingWorker {

    private final StringRedisTemplate redisTemplate;
    private final InboxRepository inboxRepository;
    private final CheckResultRepository checkResultRepository;
    private final TaskRepository taskRepository;
    private final HealthCheckService healthCheckService;

    public PingWorker(StringRedisTemplate redisTemplate,
                      InboxRepository inboxRepository,
                      CheckResultRepository checkResultRepository,
                      TaskRepository taskRepository,
                      HealthCheckService healthCheckService) {
        this.redisTemplate = redisTemplate;
        this.inboxRepository = inboxRepository;
        this.checkResultRepository = checkResultRepository;
        this.taskRepository = taskRepository;
        this.healthCheckService = healthCheckService;
    }

    @Scheduled(fixedDelay = 2000) // Опрашиваем Redis каждые 2 секунды
    public void consumeFromRedis() {
        // 1. Забираем сообщение с правого края списка (принцип FIFO: leftPush -> rightPop)
        String message = redisTemplate.opsForList().rightPop("task-queue");

        if (message == null) {
            return; // Очередь пуста
        }

        // Парсим сообщение вида "eventId|taskId|url"
        String[] parts = message.split("\\|");
        String eventId = parts[0];
        UUID taskId = UUID.fromString(parts[1]);
        String url = parts[2];

        // 2. TRANSACTIONAL INBOX: Защита от дублей
        String uniqueKey = eventId + "_" + url;
        if (!trySaveToInbox(uniqueKey)) {
            System.out.println("[INBOX] Дубль сообщения отброшен: " + uniqueKey);
            return;
        }

        // 3. РЕАЛЬНЫЙ ПИНГ СЕТИ
        System.out.println("[WORKER] Вычитал из Redis и пингую: " + url);
        int statusCode = healthCheckService.pingUrl(url);

        // 4. СОХРАНЕНИЕ РЕЗУЛЬТАТА В БД
        saveResultAndCheckCompletion(taskId, url, statusCode);
    }

    private boolean trySaveToInbox(String key) {
        try {
            inboxRepository.saveAndFlush(new InboxEvent(key));
            return true;
        } catch (Exception e) {
            return false; // Ключ уже есть в БД
        }
    }

    @Transactional
    public void saveResultAndCheckCompletion(UUID taskId, String url, int statusCode) {
        // Сохраняем факт проверки
        CheckResult result = new CheckResult();
        result.setTaskId(taskId);
        result.setUrl(url);
        result.setStatusCode(statusCode);
        result.setAvailable(statusCode >= 200 && statusCode < 400);
        checkResultRepository.save(result);

        // Проверяем: если количество проверенных сайтов совпало с общим числом в задаче -> закрываем задачу!
        taskRepository.findById(taskId).ifPresent(task -> {
            long processedCount = checkResultRepository.countByTaskId(taskId);
            if (processedCount >= task.getUrls_count()) {
                task.setStatus("COMPLETED");
                taskRepository.save(task);
                System.out.println("[WORKER] Задача " + taskId + " ПОЛНОСТЬЮ ЗАВЕРШЕНА (COMPLETED)!");
            }
        });
    }
}
