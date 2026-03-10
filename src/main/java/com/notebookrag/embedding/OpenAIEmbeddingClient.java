package com.notebookrag.embedding;

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
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP клиент для OpenAI Embeddings API.
 *
 * Генерирует векторные представления (embeddings) для текстовых фрагментов.
 *
 * API Endpoint: POST https://api.openai.com/v1/embeddings
 * Model: text-embedding-3-small (1536 dimensions)
 *
 * Важные особенности:
 * - Поддерживает batch requests (до 2048 inputs)
 * - Rate limiting: 3000 RPM для tier 1
 * - Retry logic с exponential backoff
 * - Векторы автоматически нормализованы (||v|| = 1.0)
 *
 * Пример запроса:
 * {
 *   "input": ["Hello world", "Another text"],
 *   "model": "text-embedding-3-small"
 * }
 *
 * Пример ответа:
 * {
 *   "data": [
 *     {"embedding": [0.1, -0.2, ...], "index": 0},
 *     {"embedding": [0.3, 0.1, ...], "index": 1}
 *   ],
 *   "model": "text-embedding-3-small",
 *   "usage": {"prompt_tokens": 10, "total_tokens": 10}
 * }
 */
public class OpenAIEmbeddingClient {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIEmbeddingClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxRetries;
    private final int timeoutSeconds;

    /**
     * Конструктор. Использует настройки из Configuration.
     */
    public OpenAIEmbeddingClient() {
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
        this.objectMapper = new ObjectMapper();
        this.apiKey = Configuration.getOpenAIApiKey();
        this.baseUrl = Configuration.getOpenAIBaseUrl();
        this.model = Configuration.getEmbeddingModel();
        this.maxRetries = Configuration.getMaxRetries();
        this.timeoutSeconds = Configuration.getRequestTimeoutSeconds();

        logger.info("OpenAI Embedding Client инициализирован: model={}, timeout={}s",
            model, timeoutSeconds);
    }

    /**
     * Генерирует embedding для одного текста.
     *
     * @param text текст для embedding
     * @return вектор embedding (1536 float values)
     * @throws IOException если ошибка API
     */
    public float[] generateEmbedding(String text) throws IOException, InterruptedException {
        List<String> inputs = List.of(text);
        List<float[]> embeddings = generateEmbeddings(inputs);

        if (embeddings.isEmpty()) {
            throw new IOException("API вернул пустой список embeddings");
        }

        return embeddings.get(0);
    }

    /**
     * Генерирует embeddings для нескольких текстов (batch request).
     *
     * Эффективнее чем вызывать generateEmbedding() в цикле.
     * OpenAI API поддерживает до 2048 inputs в одном запросе.
     *
     * @param texts список текстов
     * @return список векторов embeddings
     * @throws IOException если ошибка API
     */
    public List<float[]> generateEmbeddings(List<String> texts)
        throws IOException, InterruptedException {

        if (texts.isEmpty()) {
            return new ArrayList<>();
        }

        // Валидация размера batch
        if (texts.size() > 2048) {
            throw new IllegalArgumentException(
                "Слишком много текстов в batch (" + texts.size() + "). Максимум: 2048"
            );
        }

        // Построить JSON request
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);

        ArrayNode inputArray = requestBody.putArray("input");
        for (String text : texts) {
            inputArray.add(text);
        }

        String requestJson = objectMapper.writeValueAsString(requestBody);

        // Отправить запрос с retry logic
        String responseJson = sendRequestWithRetry(requestJson);

        // Распарсить response
        return parseEmbeddingsResponse(responseJson);
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
        String endpoint = baseUrl + "/embeddings";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .POST(HttpRequest.BodyPublishers.ofString(requestJson))
            .build();

        logger.debug("Отправка запроса к OpenAI Embeddings API: {} символов текста",
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
    private List<float[]> parseEmbeddingsResponse(String responseJson) throws IOException {
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode dataArray = root.get("data");

        if (dataArray == null || !dataArray.isArray()) {
            throw new IOException("Invalid response format: missing 'data' array");
        }

        List<float[]> embeddings = new ArrayList<>();

        for (JsonNode item : dataArray) {
            JsonNode embeddingNode = item.get("embedding");

            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new IOException("Invalid embedding format");
            }

            // Конвертировать JSON array в float[]
            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }

            embeddings.add(embedding);
        }

        // Логирование usage (если есть)
        JsonNode usageNode = root.get("usage");
        if (usageNode != null) {
            int totalTokens = usageNode.get("total_tokens").asInt();
            logger.debug("Embeddings сгенерированы: count={}, tokens={}",
                embeddings.size(), totalTokens);
        }

        return embeddings;
    }
}
