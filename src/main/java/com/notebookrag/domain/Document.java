package com.notebookrag.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Сущность документа.
 *
 * Представляет загруженный документ в RAG системе.
 * Документ может быть в формате TXT, PDF или DOCX.
 *
 * State transitions:
 * 1. CREATED - документ обнаружен
 * 2. PARSED - текст извлечен
 * 3. CHUNKED - разбит на чанки
 * 4. EMBEDDED - эмбеддинги созданы
 */
public class Document implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Уникальный идентификатор документа (UUID)
     */
    private final String id;

    /**
     * Путь к исходному файлу
     */
    private final String filePath;

    /**
     * Тип документа (TXT, PDF, DOCX)
     */
    private final DocumentType type;

    /**
     * Дата и время загрузки
     */
    private final LocalDateTime uploadedAt;

    /**
     * Размер файла в байтах
     */
    private final long sizeBytes;

    /**
     * Количество чанков, созданных из этого документа
     */
    private int chunkCount;

    /**
     * Извлеченный текст документа (опционально для больших документов)
     */
    private String textContent;

    /**
     * Текущее состояние обработки документа
     */
    private ProcessingState state;

    public enum ProcessingState {
        CREATED,
        PARSED,
        CHUNKED,
        EMBEDDED
    }

    /**
     * Конструктор для создания нового документа.
     */
    public Document(String filePath, DocumentType type, long sizeBytes) {
        this.id = UUID.randomUUID().toString();
        this.filePath = filePath;
        this.type = type;
        this.sizeBytes = sizeBytes;
        this.uploadedAt = LocalDateTime.now();
        this.chunkCount = 0;
        this.state = ProcessingState.CREATED;
    }

    /**
     * Полный конструктор (для десериализации).
     */
    public Document(String id, String filePath, DocumentType type, LocalDateTime uploadedAt,
                    long sizeBytes, int chunkCount, String textContent, ProcessingState state) {
        this.id = id;
        this.filePath = filePath;
        this.type = type;
        this.uploadedAt = uploadedAt;
        this.sizeBytes = sizeBytes;
        this.chunkCount = chunkCount;
        this.textContent = textContent;
        this.state = state;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getFilePath() {
        return filePath;
    }

    public DocumentType getType() {
        return type;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public String getTextContent() {
        return textContent;
    }

    public ProcessingState getState() {
        return state;
    }

    // Setters для изменяемых полей
    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public void setState(ProcessingState state) {
        this.state = state;
    }

    /**
     * Возвращает размер файла в human-readable формате (MB, KB, bytes).
     */
    public String getFormattedSize() {
        if (sizeBytes >= 1024 * 1024) {
            return String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0));
        } else if (sizeBytes >= 1024) {
            return String.format("%.2f KB", sizeBytes / 1024.0);
        } else {
            return sizeBytes + " bytes";
        }
    }

    /**
     * Валидация документа перед обработкой.
     */
    public void validate(int maxSizeMB) {
        Objects.requireNonNull(filePath, "File path не может быть null");
        Objects.requireNonNull(type, "Document type не может быть null");

        long maxSizeBytes = (long) maxSizeMB * 1024 * 1024;
        if (sizeBytes > maxSizeBytes) {
            throw new IllegalStateException(
                String.format("Размер файла (%s) превышает максимально допустимый (%d MB)",
                    getFormattedSize(), maxSizeMB)
            );
        }

        if (sizeBytes == 0) {
            throw new IllegalStateException("Файл пустой (0 bytes)");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Document document = (Document) o;
        return Objects.equals(id, document.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Document{id='%s', file='%s', type=%s, size=%s, chunks=%d, state=%s}",
            id, filePath, type, getFormattedSize(), chunkCount, state);
    }
}
