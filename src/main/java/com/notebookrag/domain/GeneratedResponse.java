package com.notebookrag.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Сгенерированный LLM ответ.
 *
 * Представляет финальный ответ RAG системы на запрос пользователя.
 * Включает:
 * - Текст ответа от LLM
 * - Список использованных чанков (sources)
 * - Метрики (токены, время генерации)
 * - Ссылку на исходный запрос
 */
public class GeneratedResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Уникальный идентификатор ответа (UUID)
     */
    private final String id;

    /**
     * ID исходного запроса
     */
    private final String queryId;

    /**
     * Текст ответа от LLM
     */
    private final String responseText;

    /**
     * Список чанков, использованных в контексте для генерации
     */
    private final List<RetrievalResult> usedChunks;

    /**
     * Количество токенов, использованных для генерации
     * (prompt tokens + completion tokens)
     */
    private final int tokensUsed;

    /**
     * Название модели LLM, использованной для генерации
     * Например: "gpt-4o-mini"
     */
    private final String model;

    /**
     * Время генерации в миллисекундах
     */
    private final long generationTimeMs;

    /**
     * Дата и время генерации ответа
     */
    private final LocalDateTime timestamp;

    /**
     * Конструктор для создания нового ответа.
     *
     * @param queryId ID исходного запроса
     * @param responseText текст ответа от LLM
     * @param usedChunks список использованных чанков
     * @param tokensUsed количество токенов
     * @param model название модели
     * @param generationTimeMs время генерации в мс
     */
    public GeneratedResponse(String queryId, String responseText,
                             List<RetrievalResult> usedChunks,
                             int tokensUsed, String model,
                             long generationTimeMs) {
        this.id = UUID.randomUUID().toString();
        this.queryId = queryId;
        this.responseText = responseText;
        this.usedChunks = new ArrayList<>(usedChunks);
        this.tokensUsed = tokensUsed;
        this.model = model;
        this.generationTimeMs = generationTimeMs;
        this.timestamp = LocalDateTime.now();

        // Валидация
        validate();
    }

    /**
     * Полный конструктор (для десериализации).
     */
    public GeneratedResponse(String id, String queryId, String responseText,
                             List<RetrievalResult> usedChunks, int tokensUsed,
                             String model, long generationTimeMs,
                             LocalDateTime timestamp) {
        this.id = id;
        this.queryId = queryId;
        this.responseText = responseText;
        this.usedChunks = usedChunks;
        this.tokensUsed = tokensUsed;
        this.model = model;
        this.generationTimeMs = generationTimeMs;
        this.timestamp = timestamp;
    }

    /**
     * Валидация ответа.
     */
    private void validate() {
        Objects.requireNonNull(queryId, "Query ID не может быть null");
        Objects.requireNonNull(responseText, "Response text не может быть null");
        Objects.requireNonNull(usedChunks, "Used chunks не могут быть null");
        Objects.requireNonNull(model, "Model не может быть null");

        if (tokensUsed < 0) {
            throw new IllegalArgumentException("Tokens used не может быть отрицательным");
        }

        if (generationTimeMs < 0) {
            throw new IllegalArgumentException("Generation time не может быть отрицательным");
        }
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getQueryId() {
        return queryId;
    }

    public String getResponseText() {
        return responseText;
    }

    public List<RetrievalResult> getUsedChunks() {
        return Collections.unmodifiableList(usedChunks);
    }

    public int getTokensUsed() {
        return tokensUsed;
    }

    public String getModel() {
        return model;
    }

    public long getGenerationTimeMs() {
        return generationTimeMs;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Возвращает количество использованных чанков (sources).
     */
    public int getSourceCount() {
        return usedChunks.size();
    }

    /**
     * Проверяет, пустой ли ответ.
     */
    public boolean isEmpty() {
        return responseText == null || responseText.trim().isEmpty();
    }

    /**
     * Возвращает время генерации в секундах.
     */
    public double getGenerationTimeSec() {
        return generationTimeMs / 1000.0;
    }

    /**
     * Форматирует время генерации как строку.
     */
    public String getFormattedGenerationTime() {
        if (generationTimeMs < 1000) {
            return generationTimeMs + " мс";
        } else {
            return String.format("%.2f сек", getGenerationTimeSec());
        }
    }

    /**
     * Возвращает список ID чанков, использованных в генерации.
     */
    public List<String> getSourceChunkIds() {
        List<String> ids = new ArrayList<>();
        for (RetrievalResult result : usedChunks) {
            ids.add(result.getChunk().getId());
        }
        return ids;
    }

    /**
     * Возвращает среднюю similarity score использованных чанков.
     */
    public double getAverageSimilarityScore() {
        if (usedChunks.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        for (RetrievalResult result : usedChunks) {
            sum += result.getSimilarityScore();
        }
        return sum / usedChunks.size();
    }

    /**
     * Проверяет, был ли использован хотя бы один чанк с высоким similarity score.
     */
    public boolean hasHighQualitySources(double threshold) {
        for (RetrievalResult result : usedChunks) {
            if (result.getSimilarityScore() >= threshold) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GeneratedResponse that = (GeneratedResponse) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format(
            "GeneratedResponse{id='%s', query='%s', model='%s', tokens=%d, sources=%d, time=%s}",
            id, queryId, model, tokensUsed, getSourceCount(), getFormattedGenerationTime()
        );
    }
}
