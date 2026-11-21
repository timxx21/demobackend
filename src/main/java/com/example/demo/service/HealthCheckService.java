package com.example.demo.service;

import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class HealthCheckService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL) // Следуем за 301/302 редиректами
            .build();

    public int pingUrl(String url) {
        try {
            URI uri = URI.create(url);
            int status = sendRequest(uri, "HEAD");

            // Если сервер не поддерживает HEAD, пробуем обычный GET
            if (status == 405 || status == 403) {
                status = sendRequest(uri, "GET");
            }
            return status;
        } catch (Exception e) {
            return 0; // Сайт упал, таймаут или неверный URL
        }
    }

    private int sendRequest(URI uri, String method) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(5)) // Общий таймаут на ожидание ответа
                .build();

        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }
}
