package com.notebookrag.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Результат поиска одного чанка.
 *
 * Представляет найденный чанк вместе с его оценкой релевантности (similarity score).
 * Используется в RAG для ранжирования найденных фрагментов текста.
 *
 * Similarity score:
 * - Диапазон: [0.0, 1.0] (для косинусного сходства)
 * - 1.0 = идеальное совпадение
 * - 0.0 = полное несовпадение
 * - Обычно релевантные чанки имеют score > 0.7
 */
public class RetrievalResult implements Serializable, Comparable<RetrievalResult> {
    private static final long serialVersionUID = 1L;

    /**
     * Найденный чанк
     */
    private final Chunk chunk;

    /**
     * Оценка сходства (similarity score)
     * Для косинусного сходства: значение в диапазоне [0.0, 1.0]
     */
    private final float similarityScore;

    /**
     * Ранг в результатах поиска (1 = самый релевантный)
     */
    private int rank;

    /**
     * Конструктор для создания результата поиска.
     *
     * @param chunk найденный чанк
     * @param similarityScore оценка сходства
     */
    public RetrievalResult(Chunk chunk, float similarityScore) {
        this.chunk = chunk;
        this.similarityScore = similarityScore;
        this.rank = 0; // Будет установлен при ранжировании

        // Валидация
        validate();
    }

    /**
     * Полный конструктор.
     */
    public RetrievalResult(Chunk chunk, float similarityScore, int rank) {
        this.chunk = chunk;
        this.similarityScore = similarityScore;
        this.rank = rank;

        validate();
    }

    /**
     * Валидация результата поиска.
     */
    private void validate() {
        Objects.requireNonNull(chunk, "Chunk не может быть null");

        if (similarityScore < 0.0f || similarityScore > 1.0f) {
            throw new IllegalArgumentException(
                String.format("Similarity score должен быть в диапазоне [0.0, 1.0]. Получено: %.4f",
                    similarityScore)
            );
        }

        if (rank < 0) {
            throw new IllegalArgumentException("Rank не может быть отрицательным");
        }
    }

    // Getters
    public Chunk getChunk() {
        return chunk;
    }

    public float getSimilarityScore() {
        return similarityScore;
    }

    public int getRank() {
        return rank;
    }

    // Setter для rank (устанавливается при сортировке)
    public void setRank(int rank) {
        if (rank < 1) {
            throw new IllegalArgumentException("Rank должен быть >= 1");
        }
        this.rank = rank;
    }

    /**
     * Форматирует similarity score как процент.
     */
    public String getFormattedScore() {
        return String.format("%.2f%%", similarityScore * 100);
    }

    /**
     * Проверяет, превышает ли similarity score заданный порог.
     */
    public boolean meetsThreshold(double threshold) {
        return similarityScore >= threshold;
    }

    /**
     * Сравнение по similarity score (для сортировки по убыванию).
     * Более высокий score должен быть первым.
     */
    @Override
    public int compareTo(RetrievalResult other) {
        // Сортировка по убыванию (больший score первым)
        return Float.compare(other.similarityScore, this.similarityScore);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RetrievalResult that = (RetrievalResult) o;
        return Float.compare(that.similarityScore, similarityScore) == 0 &&
               Objects.equals(chunk, that.chunk);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunk, similarityScore);
    }

    @Override
    public String toString() {
        return String.format("RetrievalResult{rank=%d, score=%.4f, chunk=%s}",
            rank, similarityScore, chunk.getId());
    }
}
