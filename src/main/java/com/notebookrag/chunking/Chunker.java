package com.notebookrag.chunking;

import com.notebookrag.domain.Chunk;

import java.util.List;

/**
 * Интерфейс для различных стратегий разбиения текста на чанки.
 *
 * Каждая стратегия определяет свой подход к разбиению:
 * - Fixed-size: фиксированный размер с перекрытием
 * - Semantic: по смысловым границам (будущее)
 * - Sentence-based: по предложениям (будущее)
 */
public interface Chunker {

    /**
     * Разбивает текст на чанки.
     *
     * @param text исходный текст
     * @param chunkSize размер чанка в символах
     * @param overlap размер перекрытия между чанками
     * @return список чанков (без ID и documentId, они устанавливаются позже)
     */
    List<Chunk> chunk(String text, int chunkSize, int overlap);
}
