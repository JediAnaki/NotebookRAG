package com.notebookrag.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Сущность эмбеддинга.
 *
 * Представляет векторное представление чанка текста.
 * Эмбеддинги генерируются через OpenAI Embeddings API и используются
 * для семантического поиска релевантных чанков.
 *
 * Для модели text-embedding-3-small:
 * - Размерность: 1536
 * - Тип: float[]
 * - Нормализация: vectors are normalized (cosine similarity = dot product)
 */
public class Embedding implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Уникальный идентификатор эмбеддинга (UUID)
     */
    private final String id;

    /**
     * ID чанка, для которого создан этот эмбеддинг
     */
    private final String chunkId;

    /**
     * Вектор эмбеддинга (обычно 1536 элементов для text-embedding-3-small)
     */
    private final float[] vector;

    /**
     * Размерность вектора (должна быть = vector.length)
     */
    private final int dimensions;

    /**
     * Название модели, использованной для генерации
     * Например: "text-embedding-3-small"
     */
    private final String model;

    /**
     * Дата и время генерации эмбеддинга
     */
    private final LocalDateTime generatedAt;

    /**
     * Конструктор для создания нового эмбеддинга.
     *
     * @param chunkId ID чанка
     * @param vector вектор эмбеддинга
     * @param model название модели
     */
    public Embedding(String chunkId, float[] vector, String model) {
        this.id = UUID.randomUUID().toString();
        this.chunkId = chunkId;
        this.vector = vector;
        this.dimensions = vector.length;
        this.model = model;
        this.generatedAt = LocalDateTime.now();

        // Валидация
        validate();
    }

    /**
     * Полный конструктор (для десериализации).
     */
    public Embedding(String id, String chunkId, float[] vector, int dimensions,
                     String model, LocalDateTime generatedAt) {
        this.id = id;
        this.chunkId = chunkId;
        this.vector = vector;
        this.dimensions = dimensions;
        this.model = model;
        this.generatedAt = generatedAt;
    }

    /**
     * Валидация эмбеддинга.
     */
    private void validate() {
        Objects.requireNonNull(chunkId, "Chunk ID не может быть null");
        Objects.requireNonNull(vector, "Vector не может быть null");
        Objects.requireNonNull(model, "Model не может быть null");

        if (vector.length == 0) {
            throw new IllegalArgumentException("Vector не может быть пустым");
        }

        if (dimensions != vector.length) {
            throw new IllegalArgumentException(
                String.format("Dimension mismatch: dimensions=%d, vector.length=%d",
                    dimensions, vector.length)
            );
        }

        // Проверка что все элементы вектора - валидные float числа
        for (int i = 0; i < vector.length; i++) {
            if (Float.isNaN(vector[i]) || Float.isInfinite(vector[i])) {
                throw new IllegalArgumentException(
                    "Vector содержит invalid float на позиции " + i + ": " + vector[i]
                );
            }
        }
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getChunkId() {
        return chunkId;
    }

    public float[] getVector() {
        return vector;
    }

    public int getDimensions() {
        return dimensions;
    }

    public String getModel() {
        return model;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    /**
     * Возвращает первые N компонентов вектора для отладки.
     *
     * @param count количество компонентов
     * @return массив первых N компонентов
     */
    public float[] getFirstComponents(int count) {
        int limit = Math.min(count, vector.length);
        return Arrays.copyOf(vector, limit);
    }

    /**
     * Вычисляет норму (длину) вектора.
     * Для нормализованных векторов должна быть ≈ 1.0
     *
     * @return норма вектора
     */
    public double getNorm() {
        double sum = 0.0;
        for (float v : vector) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    /**
     * Проверяет, нормализован ли вектор (норма ≈ 1.0).
     *
     * @param epsilon допустимая погрешность
     * @return true если вектор нормализован
     */
    public boolean isNormalized(double epsilon) {
        double norm = getNorm();
        return Math.abs(norm - 1.0) < epsilon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Embedding embedding = (Embedding) o;
        return Objects.equals(id, embedding.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Embedding{id='%s', chunk='%s', model='%s', dims=%d, norm=%.4f}",
            id, chunkId, model, dimensions, getNorm());
    }
}
