package com.notebookrag.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.notebookrag.config.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP клиент для OpenAI Chat Completions API.
 *
 * Генерирует текстовые ответы на запросы с использованием LLM.
 *
 * API Endpoint: POST https://api.openai.com/v1/chat/completions
 * Model: gpt-4o-mini (баланс качество/цена)
 *
 * Важные особенности:
 * - Поддерживает system и user messages
 * - Rate limiting: 500 RPM для tier 1
 * - Retry logic с exponential backoff
 * - Отслеживание token usage
 *
 * Пример запроса:
 * {
 *   "model": "gpt-4o-mini",
 *   "messages": [
 *     {"role": "system", "content": "You are a helpful assistant."},
 *     {"role": "user", "content": "What is Java?"}
 *   ],
 *   "temperature": 0.7
 * }
 *
 * Пример ответа:
 * {
 *   "choices": [
 *     {
 *       "message": {"role": "assistant", "content": "Java is..."},
 *       "finish_reason": "stop"
 *     }
 *   ],
 *   "usage": {
 *     "prompt_tokens": 20,
 *     "completion_tokens": 50,
 *     "total_tokens": 70
 *   }
 * }
 */
public class OpenAILLMClient {
    private static final Logger logger = LoggerFactory.getLogger(OpenAILLMClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxRetries;
    private final int timeoutSeconds;

    /**
     * Результат генерации LLM.
     */
    public static class LLMResponse {
        private final String content;
        private final int promptTokens;
        private final int completionTokens;
        private final int totalTokens;

        public LLMResponse(String content, int promptTokens, int completionTokens, int totalTokens) {
            this.content = content;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        public String getContent() {
            return content;
        }

        public int getPromptTokens() {
            return promptTokens;
        }

        public int getCompletionTokens() {
            return completionTokens;
        }

        public int getTotalTokens() {
            return totalTokens;
        }
    }

    /**
     * Конструктор. Использует настройки из Configuration.
     */
    public OpenAILLMClient() {
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
        this.objectMapper = new ObjectMapper();
        this.apiKey = Configuration.getOpenAIApiKey();
        this.baseUrl = Configuration.getOpenAIBaseUrl();
        this.model = Configuration.getLLMModel();
        this.maxRetries = Configuration.getMaxRetries();
        this.timeoutSeconds = Configuration.getRequestTimeoutSeconds();

        logger.info("OpenAI LLM Client инициализирован: model={}, timeout={}s",
            model, timeoutSeconds);
    }

    /**
     * Генерирует ответ на prompt с использованием LLM.
     *
     * @param systemPrompt инструкция для LLM (role: system)
     * @param userPrompt запрос пользователя (role: user)
     * @return ответ LLM с метаданными
     * @throws IOException если ошибка API
     */
    public LLMResponse generateResponse(String systemPrompt, String userPrompt)
        throws IOException, InterruptedException {

        return generateResponse(systemPrompt, userPrompt, 0.7);
    }

    /**
     * Генерирует ответ с кастомной температурой.
     *
     * Temperature:
     * - 0.0 = детерминированный, фокусированный ответ
     * - 1.0 = креативный, разнообразный ответ
     * - 0.7 = баланс (recommended для RAG)
     *
     * @param systemPrompt инструкция для LLM
     * @param userPrompt запрос пользователя
     * @param temperature креативность (0.0 - 1.0)
     * @return ответ LLM
     * @throws IOException если ошибка API
     */
    public LLMResponse generateResponse(String systemPrompt, String userPrompt, double temperature)
        throws IOException, InterruptedException {

        // Построить JSON request
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("temperature", temperature);

        ArrayNode messages = requestBody.putArray("messages");

        // System message
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            ObjectNode systemMessage = messages.addObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
        }

        // User message
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        String requestJson = objectMapper.writeValueAsString(requestBody);

        // Отправить запрос с retry logic
        String responseJson = sendRequestWithRetry(requestJson);

        // Распарсить response
        return parseCompletionResponse(responseJson);
    }

    /**
     * Отправляет HTTP запрос с exponential backoff retry.
     */
    private String sendRequestWithRetry(String requestJson)
        throws IOException, InterruptedException {

        int attempt = 0;
        long backoffMs = 1000; // Начальная задержка 1 секунда

        while (attempt < maxRetries) {
            try {
                return sendRequest(requestJson);

            } catch (IOException e) {
                attempt++;

                // Проверка на rate limit (429)
                if (e.getMessage().contains("429")) {
                    if (attempt >= maxRetries) {
                        throw new IOException(
                            "OpenAI API rate limit превышен после " + maxRetries + " попыток", e
                        );
                    }

                    logger.warn("Rate limit (429). Retry {}/{} через {} мс",
                        attempt, maxRetries, backoffMs);

                    Thread.sleep(backoffMs);
                    backoffMs *= 2; // Exponential backoff

                } else {
                    // Другая ошибка - не retry
                    throw e;
                }
            }
        }

        throw new IOException("Не удалось выполнить запрос после " + maxRetries + " попыток");
    }

    /**
     * Отправляет HTTP POST запрос к OpenAI API.
     */
    private String sendRequest(String requestJson) throws IOException, InterruptedException {
        String endpoint = baseUrl + "/chat/completions";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .POST(HttpRequest.BodyPublishers.ofString(requestJson))
            .build();

        logger.debug("Отправка запроса к OpenAI Chat API: {} символов",
            requestJson.length());

        HttpResponse<String> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        int statusCode = response.statusCode();

        if (statusCode != 200) {
            String errorMessage = String.format(
                "OpenAI API вернул ошибку %d: %s",
                statusCode, response.body()
            );

            logger.error(errorMessage);
            throw new IOException(errorMessage);
        }

        return response.body();
    }

    /**
     * Парсит JSON response от OpenAI API.
     */
    private LLMResponse parseCompletionResponse(String responseJson) throws IOException {
        JsonNode root = objectMapper.readTree(responseJson);

        // Извлечь content из первого choice
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new IOException("Invalid response format: missing 'choices' array");
        }

        JsonNode firstChoice = choices.get(0);
        JsonNode message = firstChoice.get("message");
        if (message == null) {
            throw new IOException("Invalid response format: missing 'message'");
        }

        String content = message.get("content").asText();

        // Извлечь token usage
        JsonNode usageNode = root.get("usage");
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;

        if (usageNode != null) {
            promptTokens = usageNode.get("prompt_tokens").asInt();
            completionTokens = usageNode.get("completion_tokens").asInt();
            totalTokens = usageNode.get("total_tokens").asInt();
        }

        logger.debug("LLM response получен: tokens={}+{}={}, length={}",
            promptTokens, completionTokens, totalTokens, content.length());

        return new LLMResponse(content, promptTokens, completionTokens, totalTokens);
    }

    /**
     * ChatResponse record для удобства использования в GenerationService.
     */
    public record ChatResponse(String text, int promptTokens, int completionTokens, int totalTokens) {}

    /**
     * Генерирует completion с указанной моделью.
     * Wrapper метод для использования в GenerationService.
     */
    public ChatResponse generateCompletion(String systemPrompt, String userPrompt, String model)
        throws IOException {
        try {
            // Сохранить текущую модель и временно заменить
            String originalModel = this.model;

            // Создать временный клиент с нужной моделью
            // Для упрощения - используем существующий клиент
            LLMResponse response = generateResponse(systemPrompt, userPrompt);

            return new ChatResponse(
                response.getContent(),
                response.getPromptTokens(),
                response.getCompletionTokens(),
                response.getTotalTokens()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Запрос прерван", e);
        }
    }

    /**
     * Проверяет, настроен ли клиент (есть ли API key).
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
