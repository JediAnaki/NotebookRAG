package com.notebookrag.chunking;

import com.notebookrag.domain.Chunk;
import com.notebookrag.domain.ChunkingStrategy;
import com.notebookrag.domain.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис для разбиения документов на чанки.
 *
 * Чанкинг - критически важный этап RAG пайплайна. Качество разбиения напрямую влияет на:
 * 1. Релевантность поиска (маленькие чанки = точнее, но меньше контекста)
 * 2. Качество ответов (большие чанки = больше контекста, но менее точный поиск)
 * 3. Стоимость embeddings (больше чанков = больше API запросов)
 *
 * Этот сервис поддерживает различные стратегии разбиения через паттерн Strategy.
 */
public class ChunkingService {

    private static final Logger logger = LoggerFactory.getLogger(ChunkingService.class);

    private final Map<ChunkingStrategy, Chunker> chunkers;

    public ChunkingService() {
        this.chunkers = new HashMap<>();
    }

    /**
     * Регистрирует реализацию chunker для конкретной стратегии.
     *
     * @param strategy стратегия разбиения (FIXED_SIZE, и т.д.)
     * @param chunker реализация chunker
     */
    public void registerChunker(ChunkingStrategy strategy, Chunker chunker) {
        chunkers.put(strategy, chunker);
        logger.debug("Зарегистрирован chunker для стратегии: {}", strategy);
    }

    /**
     * Разбивает документ на чанки согласно выбранной стратегии.
     *
     * Процесс:
     * 1. Выбор подходящего chunker по стратегии
     * 2. Разбиение текста на фрагменты
     * 3. Создание Chunk объектов с метаданными
     * 4. Присвоение уникальных ID каждому чанку
     *
     * @param document документ для разбиения
     * @param strategy стратегия разбиения
     * @param chunkSize размер чанка в символах
     * @param overlap размер перекрытия между чанками
     * @return список чанков
     */
    public List<Chunk> chunkDocument(Document document, ChunkingStrategy strategy,
                                     int chunkSize, int overlap) {
        logger.info("Разбиение документа {} на чанки (стратегия: {}, размер: {}, перекрытие: {})",
            document.getId(), strategy, chunkSize, overlap);

        // Валидация параметров
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Размер чанка должен быть больше 0");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("Перекрытие не может быть отрицательным");
        }
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("Перекрытие должно быть меньше размера чанка");
        }

        // Получение текста документа
        String text = document.getTextContent();
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Документ не содержит текста");
        }

        // Разбиение текста на фрагменты
        List<Chunk> chunks = new ArrayList<>();

        // Для FIXED_SIZE стратегии используем FixedSizeChunker
        if (strategy == ChunkingStrategy.FIXED_SIZE) {
            FixedSizeChunker fixedSizeChunker = (FixedSizeChunker) chunkers.get(strategy);
            if (fixedSizeChunker == null) {
                throw new UnsupportedOperationException(
                    "FixedSizeChunker не зарегистрирован"
                );
            }

            // Получаем данные чанков
            List<FixedSizeChunker.ChunkData> chunkDataList =
                fixedSizeChunker.chunkData(text, chunkSize, overlap);

            // Создаем объекты Chunk с правильными ID и метаданными
            for (FixedSizeChunker.ChunkData data : chunkDataList) {
                Chunk chunk = new Chunk(
                    document.getId(),           // documentId
                    data.content,               // content
                    data.startPosition,         // startPosition
                    data.endPosition,           // endPosition
                    strategy,                   // strategy
                    overlap                     // overlapSize
                );
                chunks.add(chunk);
            }
        } else {
            throw new UnsupportedOperationException(
                "Стратегия " + strategy + " пока не поддерживается"
            );
        }

        logger.info("Создано {} чанков из документа {} (стратегия: {})",
            chunks.size(), document.getId(), strategy);

        // Обновляем счетчик чанков в документе
        document.setChunkCount(chunks.size());

        return chunks;
    }
}
