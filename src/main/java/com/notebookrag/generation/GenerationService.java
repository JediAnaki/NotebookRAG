package com.notebookrag.generation;

import com.notebookrag.domain.GeneratedResponse;
import com.notebookrag.domain.Query;
import com.notebookrag.domain.RetrievalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Сервис для генерации ответов через LLM (Large Language Model).
 *
 * Использует OpenAI Chat Completions API для генерации ответов на основе
 * извлеченных релевантных чанков. Это компонент "Generation" в RAG.
 *
 * Ключевые обязанности:
 * 1. Конструирование промпта (system + user query + retrieved chunks)
 * 2. Вызов LLM API
 * 3. Обработка ответа и метрик (tokens, time)
 */
public class GenerationService {
    private static final Logger logger = LoggerFactory.getLogger(GenerationService.class);

    private final OpenAILLMClient llmClient;
    private final String defaultModel;

    // System prompt для RAG
    private static final String SYSTEM_PROMPT =
        """
        Ты полезный ассистент, который отвечает на вопросы на основе предоставленного контекста.

        Правила:
        1. Отвечай только на основе предоставленного контекста
        2. Если в контексте нет информации для ответа, честно скажи об этом
        3. Не придумывай информацию, которой нет в контексте
        4. Отвечай кратко и по делу
        5. Используй русский язык
        """;

    public GenerationService() {
        this.llmClient = new OpenAILLMClient();
        this.defaultModel = "gpt-4o-mini";
    }

    public GenerationService(OpenAILLMClient llmClient, String model) {
        this.llmClient = llmClient;
        this.defaultModel = model;
    }

    /**
     * Генерирует ответ на query на основе извлеченных чанков.
     *
     * @param query запрос пользователя
     * @param retrievedChunks топ-K релевантных чанков
     * @return сгенерированный ответ с метаданными
     * @throws IOException если LLM API вернул ошибку
     */
    public GeneratedResponse generateResponse(Query query, List<RetrievalResult> retrievedChunks)
        throws IOException {
        return generateResponse(query, retrievedChunks, defaultModel);
    }

    /**
     * Генерирует ответ с указанной моделью LLM.
     *
     * @param query запрос пользователя
     * @param retrievedChunks топ-K релевантных чанков
     * @param model модель LLM для использования
     * @return сгенерированный ответ с метаданными
     * @throws IOException если LLM API вернул ошибку
     */
    public GeneratedResponse generateResponse(Query query, List<RetrievalResult> retrievedChunks, String model)
        throws IOException {

        logger.debug("Начинается генерация ответа: query='{}', chunks={}, model={}",
            query.getText(), retrievedChunks.size(), model);

        long startTime = System.currentTimeMillis();

        // Построить промпт
        String fullPrompt = constructPrompt(query.getText(), retrievedChunks);

        logger.debug("Промпт сконструирован: {} символов", fullPrompt.length());

        // Вызвать LLM API
        OpenAILLMClient.ChatResponse chatResponse = llmClient.generateCompletion(
            SYSTEM_PROMPT,
            fullPrompt,
            model
        );

        long endTime = System.currentTimeMillis();
        long generationTimeMs = endTime - startTime;

        // Создать GeneratedResponse
        GeneratedResponse response = new GeneratedResponse(
            UUID.randomUUID().toString(),
            query.getId(),
            chatResponse.text(),
            retrievedChunks,
            chatResponse.totalTokens(),
            model,
            generationTimeMs,
            LocalDateTime.now()
        );

        logger.info("Генерация завершена: tokens={}, time={}ms",
            chatResponse.totalTokens(), generationTimeMs);

        return response;
    }

    /**
     * Конструирует промпт для LLM из query и retrieved chunks.
     *
     * Формат:
     * Контекст:
     * [Chunk 1 content]
     * [Chunk 2 content]
     * ...
     *
     * Вопрос: [query text]
     *
     * @param queryText текст запроса
     * @param retrievedChunks извлеченные релевантные чанки
     * @return полный промпт для LLM
     */
    String constructPrompt(String queryText, List<RetrievalResult> retrievedChunks) {
        StringBuilder prompt = new StringBuilder();

        // Добавить контекст из retrieved chunks
        if (!retrievedChunks.isEmpty()) {
            prompt.append("Контекст:\n\n");

            for (int i = 0; i < retrievedChunks.size(); i++) {
                RetrievalResult result = retrievedChunks.get(i);
                prompt.append("--- Фрагмент ").append(i + 1).append(" ---\n");
                prompt.append(result.getChunk().getContent());
                prompt.append("\n\n");
            }
        } else {
            prompt.append("Контекст: (нет релевантной информации)\n\n");
        }

        // Добавить вопрос
        prompt.append("Вопрос: ").append(queryText).append("\n\n");
        prompt.append("Ответ:");

        return prompt.toString();
    }

    /**
     * Проверяет доступность LLM API.
     */
    public boolean isAvailable() {
        try {
            return llmClient.isConfigured();
        } catch (Exception e) {
            logger.error("Ошибка проверки доступности LLM: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Возвращает модель LLM по умолчанию.
     */
    public String getDefaultModel() {
        return defaultModel;
    }
}
