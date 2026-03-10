package com.notebookrag.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Сущность запроса пользователя.
 *
 * Представляет вопрос, заданный пользователем в RAG системе.
 * Запрос включает:
 * - Текст вопроса
 * - Эмбеддинг запроса (генерируется)
 * - Параметры поиска (top-K)
 */
public class Query implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Уникальный идентификатор запроса (UUID)
     */
    private final String id;

    /**
     * Текст запроса пользователя
     */
    private final String text;

    /**
     * Эмбеддинг запроса (генерируется через OpenAI API)
     */
    private float[] queryEmbedding;

    /**
     * Дата и время запроса
     */
    private final LocalDateTime timestamp;

    /**
     * Количество результатов для извлечения (top-K)
     */
    private final int topK;

    /**
     * Конструктор для создания нового запроса.
     *
     * @param text текст запроса
     * @param topK количество результатов для извлечения
     */
    public Query(String text, int topK) {
        this.id = UUID.randomUUID().toString();
        this.text = text;
        this.topK = topK;
        this.timestamp = LocalDateTime.now();

        // Валидация
        validate();
    }

    /**
     * Полный конструктор (для десериализации).
     */
    public Query(String id, String text, float[] queryEmbedding,
                 LocalDateTime timestamp, int topK) {
        this.id = id;
        this.text = text;
        this.queryEmbedding = queryEmbedding;
        this.timestamp = timestamp;
        this.topK = topK;
    }

    /**
     * Валидация запроса.
     */
    private void validate() {
        Objects.requireNonNull(text, "Query text не может быть null");

        if (text.trim().isEmpty()) {
            throw new IllegalArgumentException("Query text не может быть пустым");
        }

        // OpenAI limit для embeddings
        if (text.length() > 8000) {
            throw new IllegalArgumentException(
                String.format("Query text слишком длинный (%d символов). Максимум: 8000",
                    text.length())
            );
        }

        if (topK <= 0) {
            throw new IllegalArgumentException("Top-K должен быть положительным числом");
        }

        if (topK > 100) {
            throw new IllegalArgumentException(
                "Top-K слишком большой (" + topK + "). Максимум: 100"
            );
        }
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public float[] getQueryEmbedding() {
        return queryEmbedding;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getTopK() {
        return topK;
    }

    // Setter для queryEmbedding (устанавливается после генерации)
    public void setQueryEmbedding(float[] queryEmbedding) {
        this.queryEmbedding = queryEmbedding;
    }

    /**
     * Проверяет, сгенерирован ли эмбеддинг для этого запроса.
     */
    public boolean hasEmbedding() {
        return queryEmbedding != null && queryEmbedding.length > 0;
    }

    /**
     * Возвращает превью запроса (первые N символов).
     */
    public String getPreview(int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Query query = (Query) o;
        return Objects.equals(id, query.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Query{id='%s', text='%s', topK=%d, hasEmbedding=%b}",
            id, getPreview(50), topK, hasEmbedding());
    }
}
