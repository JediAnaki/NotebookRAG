package com.notebookrag.embedding;

import com.notebookrag.domain.Chunk;
import com.notebookrag.domain.Embedding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сервис для генерации эмбеддингов (векторных представлений) текстов.
 *
 * Эмбеддинги - ключевой компонент RAG. Они преобразуют текст в многомерные векторы,
 * где семантически похожие тексты находятся близко в векторном пространстве.
 *
 * Пример:
 * - "Java - это язык программирования" → [0.23, -0.45, 0.12, ...]
 * - "Python - это язык программирования" → [0.22, -0.44, 0.13, ...] (близко!)
 * - "Кошка сидит на дереве" → [-0.67, 0.34, -0.89, ...] (далеко)
 *
 * Это позволяет находить релевантные чанки даже если в запросе используются
 * другие слова (синонимы, перефразирование).
 */
public class EmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);

    private final OpenAIEmbeddingClient embeddingClient;
    private final String model;
    private final int dimensions;

    // Максимальное количество текстов в одном batch запросе к OpenAI API
    private static final int MAX_BATCH_SIZE = 100;

    /**
     * Создает сервис эмбеддингов с параметрами по умолчанию.
     */
    public EmbeddingService() {
        this(new OpenAIEmbeddingClient(), "text-embedding-3-small", 1536);
    }

    /**
     * Создает сервис эмбеддингов с заданными параметрами.
     *
     * @param embeddingClient клиент для OpenAI Embeddings API
     * @param model название модели (например, "text-embedding-3-small")
     * @param dimensions размерность векторов (1536 для text-embedding-3-small)
     */
    public EmbeddingService(OpenAIEmbeddingClient embeddingClient, String model, int dimensions) {
        this.embeddingClient = embeddingClient;
        this.model = model;
        this.dimensions = dimensions;
        logger.info("Инициализирован EmbeddingService (модель: {}, размерность: {})",
            model, dimensions);
    }

    /**
     * Возвращает модель эмбеддинга.
     */
    public String getModel() {
        return model;
    }

    /**
     * Генерирует эмбеддинг для одного текста.
     *
     * @param text текст для преобразования
     * @return вектор эмбеддинга
     * @throws IOException если произошла ошибка API
     */
    public float[] generateEmbedding(String text) throws IOException {
        logger.debug("Генерация эмбеддинга для текста длиной {} символов", text.length());

        long startTime = System.currentTimeMillis();

        float[] vector;
        try {
            vector = embeddingClient.generateEmbedding(text);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Прервано во время генерации эмбеддинга", e);
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.debug("Эмбеддинг сгенерирован за {} мс", duration);

        // Валидация размерности
        if (vector.length != dimensions) {
            throw new IllegalStateException(String.format(
                "Неожиданная размерность эмбеддинга: ожидалось %d, получено %d",
                dimensions, vector.length
            ));
        }

        return vector;
    }

    /**
     * Генерирует эмбеддинги для списка чанков с поддержкой батчинга.
     *
     * Батчинг критически важен для производительности:
     * - Вместо 100 отдельных API запросов делаем 1 batch запрос
     * - Экономия времени: ~100 секунд → ~2 секунды
     * - Экономия денег: меньше overhead на HTTP запросы
     *
     * OpenAI позволяет отправлять до 2048 текстов в одном batch запросе.
     *
     * @param chunks список чанков для обработки
     * @return список эмбеддингов (в том же порядке, что и чанки)
     * @throws IOException если произошла ошибка API
     */
    public List<Embedding> generateEmbeddingsForChunks(List<Chunk> chunks) throws IOException {
        logger.info("Начало генерации эмбеддингов для {} чанков (batch mode)", chunks.size());

        List<Embedding> embeddings = new ArrayList<>();
        long totalStartTime = System.currentTimeMillis();

        // Разбиваем чанки на батчи
        int totalChunks = chunks.size();
        int batchCount = (int) Math.ceil((double) totalChunks / MAX_BATCH_SIZE);

        logger.debug("Разбито на {} батчей (размер батча: до {})", batchCount, MAX_BATCH_SIZE);

        for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
            int startIdx = batchIndex * MAX_BATCH_SIZE;
            int endIdx = Math.min(startIdx + MAX_BATCH_SIZE, totalChunks);

            List<Chunk> batchChunks = chunks.subList(startIdx, endIdx);
            logger.debug("Обработка батча {}/{}: чанки {}-{}",
                batchIndex + 1, batchCount, startIdx + 1, endIdx);

            // Извлекаем тексты из чанков
            List<String> texts = new ArrayList<>();
            for (Chunk chunk : batchChunks) {
                texts.add(chunk.getContent());
            }

            // Генерируем эмбеддинги батчем
            long batchStartTime = System.currentTimeMillis();
            List<float[]> batchVectors;
            try {
                batchVectors = embeddingClient.generateEmbeddings(texts);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Прервано во время генерации эмбеддингов", e);
            }
            long batchDuration = System.currentTimeMillis() - batchStartTime;

            logger.debug("Батч {}/{} обработан за {} мс",
                batchIndex + 1, batchCount, batchDuration);

            // Валидация: количество векторов должно совпадать с количеством текстов
            if (batchVectors.size() != texts.size()) {
                throw new IllegalStateException(String.format(
                    "Несоответствие размеров: отправлено %d текстов, получено %d векторов",
                    texts.size(), batchVectors.size()
                ));
            }

            // Создаем объекты Embedding
            for (int i = 0; i < batchChunks.size(); i++) {
                Chunk chunk = batchChunks.get(i);
                float[] vector = batchVectors.get(i);

                Embedding embedding = new Embedding(chunk.getId(), vector, model);
                embeddings.add(embedding);
            }
        }

        long totalDuration = System.currentTimeMillis() - totalStartTime;
        logger.info("Генерация эмбеддингов завершена: {} чанков за {} мс ({} мс на чанк)",
            totalChunks, totalDuration, totalDuration / totalChunks);

        return embeddings;
    }

    /**
     * Генерирует эмбеддинг для текстового запроса пользователя.
     *
     * Используется та же модель, что и для чанков, чтобы обеспечить
     * сопоставимость векторов при поиске.
     *
     * @param queryText текст запроса
     * @return вектор эмбеддинга
     * @throws IOException если произошла ошибка API
     */
    public float[] generateQueryEmbedding(String queryText) throws IOException {
        logger.debug("Генерация эмбеддинга для запроса: \"{}\"", queryText);
        return generateEmbedding(queryText);
    }

    public int getDimensions() {
        return dimensions;
    }
}
