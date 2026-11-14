# Паттерн PRODUCER CONSUMER

Сервис для асинхронной проверки доступности списка URL-адресов. Проект реализует базовый паттерн **Transactional Outbox** для гарантированной доставки задач и **Transactional Inbox** для защиты от повторной обработки (идемпотентности).

## 🛠 Технологический стек
*   **Java 23**
*   **Spring Boot 4.1.1** (Web MVC, Data JPA, Data Redis, Scheduling)
*   **PostgreSQL** (основное хранилище)
*   **Redis** (брокер сообщений)
*   **Lombok**
*   **Docker & Docker Compose** (для инфраструктуры)

## 🏗 Архитектура и паттерны
Проект построен на основе паттернов, обеспечивающих надежность:

1.  **Transactional Outbox**: В `TaskController` при создании задачи (метод `createTask`) запись о задаче (`TaskResponse`) и событие для её обработки (`OutboxEvent`) сохраняются в одной транзакции (аннотация `@Transactional`). Это гарантирует, что задача не потеряется.
2.  **Transactional Inbox**: Перед обработкой события шедулер (`OutboxRelay`) пытается сохранить его ID в таблицу `inbox_events` через `saveAndFlush`. Если запись уже существует, вылетает исключение, которое перехватывается, и событие помечается как дубль.
3.  **Обработка событий**: Шедулер `OutboxRelay` запускается каждые 5 секунд (`@Scheduled(fixedDelay = 5000)`), вытаскивает события со статусом `QUEUED`, пушит URL-адреса в очередь Redis (`task-queue`) и выполняет их синхронный HTTP-пинг с помощью `HealthCheckService`.
4.  **Инфраструктура**: В `docker-compose.yml` описаны два сервиса — `postgres-db` (порт `5433`) и `redis-broker` (порт `6379`).

## 📋 Жизненный цикл задачи (Статусы)
*   `QUEUED` — задача создана и ожидает обработки.
*   `PROCESSED` — все URL успешно пингануты (или событие пропущено как дубль).

## 🚀 Запуск проекта

### 1. Поднять инфраструктуру (PostgreSQL + Redis)
```bash
docker compose up -d
```
### 2. Настроить подключение
Убедись, что в `src/main/resources/application.properties` указаны корректные параметры подключения (по умолчанию в проекте уже настроены под Docker):

```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/health_checker
spring.datasource.username=admin
spring.datasource.password=secret_password
spring.data.redis.host=localhost
spring.data.redis.port=6379
```
### 3. Запустить приложение
   Запусти класс DemoApplication в IntelliJ IDEA или через Maven:
```bash
./mvnw spring-boot:run
```
### 4. Отправить тестовый запрос
```bash
curl.exe -X POST http://localhost:8080/api/v1/task -H "Content-Type: application/json" -d '{\"urls\":[\"https://ya.ru\",\"https://google.com\"]}'
```
После отправки запроса в логах приложения вы увидите, как шедулер подхватывает задачу, пушит URL в Redis, проверяет Inbox и выполняет HTTP-пинги.

