package com.example.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
public class HealthCheckService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public int pingUrl(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (java.net.http.HttpTimeoutException _) {
            // Специально ловим таймаут, чтобы было видно в логах
            log.error("[HTTP TIMEOUT] Сайт {} не ответил за 5 секунд", url);
            return 0;
        } catch (Exception e) {
            log.error("[HTTP ERROR] Ошибка пинга для {} : {}", url, e.getMessage());
            return 0;
        }

    }
}
