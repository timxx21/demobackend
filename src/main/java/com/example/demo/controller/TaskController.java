package com.example.demo.controller;

import com.example.demo.dto.OutboxEvent;
import com.example.demo.dto.TaskRequest;
import com.example.demo.dto.TaskResponse;
import com.example.demo.repository.OutboxRepository;
import com.example.demo.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/task")
public class TaskController {

    private final TaskRepository taskRepository;
    private final OutboxRepository outboxRepository;

    // Конструктор внедрения зависимостей для двух репозиториев
    public TaskController(TaskRepository taskRepository, OutboxRepository outboxRepository) {
        this.taskRepository = taskRepository;
        this.outboxRepository = outboxRepository;
    }

    @PostMapping
    @Transactional // Гарантирует, что и задача, и outbox запишутся вместе
    public ResponseEntity<TaskResponse> createTask(@RequestBody TaskRequest request) {

        // 1. Собираем объект задачи. Передаем null в ID (база сгенерирует сама)
        TaskResponse task = new TaskResponse(
                null,
                "QUEUED",
                request.getUrls().size()
        );

        // 2. СОХРАНЯЕМ В БАЗУ. После этой строчки в savedTask появится реальный ID из базы!
        TaskResponse savedTask = taskRepository.save(task);

            OutboxEvent event = new OutboxEvent();

            event.setTaskId(savedTask.getTask_id());
            event.setUrls(request.getUrls());
            event.setStatus("QUEUED");

            outboxRepository.save(event);


        // 4. Возвращаем клиенту объект savedTask, там уже будет красивый ID, а не null
        return new ResponseEntity<>(savedTask, HttpStatus.CREATED);
    }
}
