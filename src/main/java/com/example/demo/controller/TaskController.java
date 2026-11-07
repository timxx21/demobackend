package com.example.demo.controller;

import com.example.demo.dto.OutboxEvent;
import com.example.demo.dto.TaskRequest;
import com.example.demo.dto.TaskResponse;
import com.example.demo.repository.OutboxRepository;
import com.example.demo.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/task")
public class TaskController {

    private final TaskRepository taskRepository; // Внедряем нашу базу
    private final OutboxRepository outboxRepository;

    public TaskController(TaskRepository taskRepository, OutboxRepository outboxRepository) {
        this.taskRepository = taskRepository;
        this.outboxRepository = outboxRepository;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@RequestBody TaskRequest request) {
        // 1. Собираем объект задачи

        TaskResponse task = new TaskResponse(
                null,
                "QUEUED",
                request.getUrls().size()
        );

        // 2. СОХРАНЯЕМ В ПОСТГРЕС ОДНОЙ СТРОЧКОЙ!
        TaskResponse savedTask = taskRepository.save(task);
        // 3. логика аутбокс
        OutboxEvent event = new OutboxEvent();

        event.setTaskId(savedTask.getTask_id());
        event.setUrls(request.getUrls());
        event.setStatus("QUEUED");

        outboxRepository.save(event);

        // 4. Возвращаем клиенту
        return new ResponseEntity<>(savedTask, HttpStatus.CREATED);
    }
}

