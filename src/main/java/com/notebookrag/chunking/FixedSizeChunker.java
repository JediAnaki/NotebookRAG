package com.notebookrag.chunking;

import com.notebookrag.domain.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Реализация стратегии разбиения фиксированного размера с перекрытием.
 *
 * Это простейшая стратегия разбиения:
 * - Разбивает текст на фрагменты фиксированной длины
 * - Добавляет перекрытие между соседними чанками
 * - Детерминированная (один и тот же текст всегда дает одинаковые чанки)
 *
 * Перекрытие важно для RAG: оно предотвращает потерю контекста на границах чанков.
 * Например, если предложение разрезано пополам, перекрытие сохранит его целиком
 * хотя бы в одном из соседних чанков.
 *
 * Пример с chunk_size=10, overlap=3:
 * Text: "This is a sample text for chunking"
 * Chunk 1: "This is a "
 * Chunk 2: "is a sample"  (перекрытие: "is a ")
 * Chunk 3: "ple text f"   (перекрытие: "ple ")
 * ...
 */
public class FixedSizeChunker implements Chunker {

    private static final Logger logger = LoggerFactory.getLogger(FixedSizeChunker.class);

    /**
     * Вспомогательный класс для передачи данных чанка.
     */
    public static class ChunkData {
        public final String content;
        public final int startPosition;
        public final int endPosition;

        public ChunkData(String content, int startPosition, int endPosition) {
            this.content = content;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
        }
    }

    /**
     * Разбивает текст на фрагменты и возвращает данные чанков (без создания объектов Chunk).
     */
    public List<ChunkData> chunkData(String text, int chunkSize, int overlap) {
        List<ChunkData> chunks = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            logger.warn("Попытка разбиения пустого текста");
            return chunks;
        }

        int textLength = text.length();
        int startPosition = 0;
        int step = chunkSize - overlap;

        while (startPosition < textLength) {
            int endPosition = Math.min(startPosition + chunkSize, textLength);
            String chunkContent = text.substring(startPosition, endPosition);

            chunks.add(new ChunkData(chunkContent, startPosition, endPosition));

            logger.trace("Создан чанк #{}: позиция {}-{}, размер {} символов",
                chunks.size(), startPosition, endPosition, chunkContent.length());

            if (endPosition >= textLength) {
                break;
            }

            startPosition += step;
        }

        logger.debug("Разбиение завершено: создано {} чанков", chunks.size());
        return chunks;
    }

    @Override
    public List<Chunk> chunk(String text, int chunkSize, int overlap) {
        // This method should not be called directly since chunks need documentId.
        // Use chunkDocument in ChunkingService instead.
        throw new UnsupportedOperationException(
            "Use ChunkingService.chunkDocument() instead - chunks require documentId"
        );
    }
}
