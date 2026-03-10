package com.notebookrag.storage;

import com.notebookrag.domain.Chunk;
import com.notebookrag.domain.Document;
import com.notebookrag.domain.Embedding;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Интерфейс для хранения векторов (embeddings) и связанных с ними данных.
 *
 * В Phase 1 используется простая файловая сериализация (.ser files).
 * В будущем может быть заменена на векторную БД (Apache Lucene, ChromaDB и т.д.).
 *
 * Структура хранилища:
 * data/vectors/
 * ├── {document-id}/
 * │   ├── metadata.ser     (Document object)
 * │   ├── chunks.ser       (List<Chunk>)
 * │   └── embeddings.ser   (List<Embedding>)
 * └── index.ser            (Map<String, String> - docId -> filePath)
 */
public interface VectorStorageService {

    /**
     * Сохраняет документ и его чанки с эмбеддингами.
     *
     * @param document документ
     * @param chunks список чанков
     * @param embeddings список эмбеддингов (должен соответствовать chunks)
     * @throws IOException если ошибка сохранения
     */
    void saveDocument(Document document, List<Chunk> chunks, List<Embedding> embeddings)
        throws IOException;

    /**
     * Загружает документ по ID.
     *
     * @param documentId ID документа
     * @return Optional с документом, или empty если не найден
     * @throws IOException если ошибка чтения
     */
    Optional<Document> loadDocument(String documentId) throws IOException;

    /**
     * Загружает все чанки документа.
     *
     * @param documentId ID документа
     * @return список чанков
     * @throws IOException если ошибка чтения
     */
    List<Chunk> loadChunks(String documentId) throws IOException;

    /**
     * Загружает все эмбеддинги документа.
     *
     * @param documentId ID документа
     * @return список эмбеддингов
     * @throws IOException если ошибка чтения
     */
    List<Embedding> loadEmbeddings(String documentId) throws IOException;

    /**
     * Загружает все эмбеддинги из всех документов в хранилище.
     * Используется для поиска релевантных чанков.
     *
     * @return список всех эмбеддингов
     * @throws IOException если ошибка чтения
     */
    List<Embedding> loadAllEmbeddings() throws IOException;

    /**
     * Загружает все документы из хранилища.
     *
     * @return список всех документов
     * @throws IOException если ошибка чтения
     */
    List<Document> loadAllDocuments() throws IOException;

    /**
     * Проверяет, существует ли документ в хранилище.
     *
     * @param documentId ID документа
     * @return true если документ существует
     */
    boolean documentExists(String documentId);

    /**
     * Удаляет документ и все связанные данные.
     *
     * @param documentId ID документа
     * @throws IOException если ошибка удаления
     */
    void deleteDocument(String documentId) throws IOException;

    /**
     * Возвращает количество документов в хранилище.
     *
     * @return количество документов
     */
    int getDocumentCount();

    /**
     * Возвращает общее количество чанков во всех документах.
     *
     * @return общее количество чанков
     */
    int getTotalChunkCount();

    /**
     * Очищает все хранилище (удаляет все документы).
     * ВНИМАНИЕ: операция необратима!
     *
     * @throws IOException если ошибка удаления
     */
    void clearAll() throws IOException;
}
