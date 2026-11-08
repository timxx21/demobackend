package com.example.demo.service;
import lombok.extern.slf4j.Slf4j;
import com.example.demo.dto.OutboxEvent;
import com.example.demo.repository.OutboxRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final StringRedisTemplate redisTemplate; // Наш инструмент работы с Redis

    public OutboxRelay(OutboxRepository outboxRepository, StringRedisTemplate redisTemplate) {
        this.outboxRepository = outboxRepository;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedDelay = 5000) // Метод запускается автоматически каждые 5 секунд!
    public void processOutbox() {
        // 1. Вытаскиваем из СУБД задачи, готовые к отправке
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatus("QUEUED");

        if (pendingEvents.isEmpty()) return;

        log.info("=== OutboxRelay: Найдено пакетов для обработки: {} ===", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // 2. Пушим сообщение в очередь Redis под именем "task-queue"
                for (String url : event.getUrls()) {
                    redisTemplate.opsForList().leftPush("task-queue", url);

                    // 3. Если Redis принял пакет — меняем статус в PostgreSQL на PROCESSED
                    event.setStatus("PROCESSED");
                    outboxRepository.save(event);

                    log.info("=== OutboxRelay: Событие {} успешно улетело в Redis! ===", event.getId());
                }
            } catch (Exception e) {
                log.error("[REDIS ERROR] Брокер недоступен: {}", e.getMessage());
                // Если Redis недоступен — статус остается PENDING, транзакция не теряется!
            }
        }
    }
}
