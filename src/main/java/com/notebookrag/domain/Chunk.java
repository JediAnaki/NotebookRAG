package com.notebookrag.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Сущность чанка (фрагмента текста).
 *
 * Представляет один фрагмент документа, созданный в процессе chunking.
 * Каждый чанк имеет:
 * - Уникальный ID
 * - Ссылку на родительский Document
 * - Текстовое содержимое
 * - Позицию в исходном тексте
 * - Метаданные о стратегии разбиения
 */
public class Chunk implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Уникальный идентификатор чанка (UUID)
     */
    private final String id;

    /**
     * ID родительского документа
     */
    private final String documentId;

    /**
     * Текстовое содержимое чанка
     */
    private final String content;

    /**
     * Позиция начала чанка в исходном тексте (в символах)
     */
    private final int startPosition;

    /**
     * Позиция конца чанка в исходном тексте (в символах)
     */
    private final int endPosition;

    /**
     * Размер чанка в символах (должен быть = content.length())
     */
    private final int sizeChars;

    /**
     * Стратегия разбиения, использованная для создания этого чанка
     */
    private final ChunkingStrategy strategy;

    /**
     * Размер перекрытия с предыдущим чанком (в символах)
     */
    private final int overlapSize;

    /**
     * Дата и время создания чанка
     */
    private final LocalDateTime createdAt;

    /**
     * Конструктор для создания нового чанка.
     *
     * @param documentId ID родительского документа
     * @param content текстовое содержимое
     * @param startPosition позиция начала в исходном тексте
     * @param endPosition позиция конца в исходном тексте
     * @param strategy стратегия chunking
     * @param overlapSize размер перекрытия
     */
    public Chunk(String documentId, String content, int startPosition, int endPosition,
                 ChunkingStrategy strategy, int overlapSize) {
        this.id = UUID.randomUUID().toString();
        this.documentId = documentId;
        this.content = content;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.sizeChars = content.length();
        this.strategy = strategy;
        this.overlapSize = overlapSize;
        this.createdAt = LocalDateTime.now();

        // Валидация
        validate();
    }

    /**
     * Полный конструктор (для десериализации).
     */
    public Chunk(String id, String documentId, String content, int startPosition, int endPosition,
                 int sizeChars, ChunkingStrategy strategy, int overlapSize, LocalDateTime createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.content = content;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.sizeChars = sizeChars;
        this.strategy = strategy;
        this.overlapSize = overlapSize;
        this.createdAt = createdAt;
    }

    /**
     * Валидация чанка.
     */
    private void validate() {
        Objects.requireNonNull(documentId, "Document ID не может быть null");
        Objects.requireNonNull(content, "Content не может быть null");
        Objects.requireNonNull(strategy, "Chunking strategy не может быть null");

        if (content.isEmpty()) {
            throw new IllegalArgumentException("Content не может быть пустым");
        }

        if (startPosition < 0) {
            throw new IllegalArgumentException("Start position не может быть отрицательным");
        }

        if (endPosition <= startPosition) {
            throw new IllegalArgumentException(
                "End position должен быть больше start position"
            );
        }

        if (sizeChars != content.length()) {
            throw new IllegalArgumentException(
                String.format("Size mismatch: sizeChars=%d, content.length()=%d",
                    sizeChars, content.length())
            );
        }

        if (overlapSize < 0) {
            throw new IllegalArgumentException("Overlap size не может быть отрицательным");
        }

        if (overlapSize >= sizeChars) {
            throw new IllegalArgumentException(
                "Overlap size не может быть больше или равен размеру чанка"
            );
        }
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getContent() {
        return content;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public int getEndPosition() {
        return endPosition;
    }

    public int getSizeChars() {
        return sizeChars;
    }

    public ChunkingStrategy getStrategy() {
        return strategy;
    }

    public int getOverlapSize() {
        return overlapSize;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Возвращает превью чанка (первые N символов).
     *
     * @param maxLength максимальная длина превью
     * @return превью текста
     */
    public String getPreview(int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    /**
     * Возвращает информацию о позиции в формате "start-end".
     */
    public String getPositionRange() {
        return startPosition + "-" + endPosition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chunk chunk = (Chunk) o;
        return Objects.equals(id, chunk.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Chunk{id='%s', doc='%s', pos=%s, size=%d, strategy=%s}",
            id, documentId, getPositionRange(), sizeChars, strategy.getCode());
    }
}
