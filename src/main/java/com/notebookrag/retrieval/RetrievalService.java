package com.notebookrag.retrieval;

import com.notebookrag.domain.Chunk;
import com.notebookrag.domain.Embedding;
import com.notebookrag.domain.RetrievalResult;
import com.notebookrag.storage.SerializationStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Сервис для поиска релевантных чанков на основе similarity search.
 *
 * Использует косинусное сходство для сравнения векторов эмбеддингов запроса
 * с эмбеддингами чанков в хранилище. Возвращает top-K наиболее релевантных чанков.
 *
 * Это ключевой компонент RAG: Retrieval - извлечение релевантного контекста
 * перед генерацией ответа.
 */
public class RetrievalService {
    private static final Logger logger = LoggerFactory.getLogger(RetrievalService.class);

    private final SerializationStorage storage;
    private final CosineSimilarity similarityCalculator;

    public RetrievalService() {
        this.storage = new SerializationStorage();
        this.similarityCalculator = new CosineSimilarity();
    }

    public RetrievalService(SerializationStorage storage) {
        this.storage = storage;
        this.similarityCalculator = new CosineSimilarity();
    }

    /**
     * Выполняет top-K retrieval: находит K наиболее релевантных чанков для query embedding.
     *
     * Алгоритм:
     * 1. Загружает все эмбеддинги и чанки из хранилища
     * 2. Вычисляет cosine similarity между query embedding и каждым chunk embedding
     * 3. Сортирует по similarity score (по убыванию)
     * 4. Возвращает top-K результатов
     *
     * @param queryEmbedding вектор эмбеддинга запроса
     * @param topK количество чанков для извлечения
     * @return список результатов поиска, отсортированный по релевантности
     * @throws IOException если не удалось загрузить данные из хранилища
     */
    public List<RetrievalResult> retrieveTopK(float[] queryEmbedding, int topK) throws IOException {
        logger.debug("Начинается retrieval: topK={}", topK);

        // Загрузить все эмбеддинги и чанки
        List<Embedding> allEmbeddings = storage.loadAllEmbeddings();
        List<Chunk> allChunks = loadAllChunks();

        if (allEmbeddings.isEmpty()) {
            logger.warn("Хранилище пустое - нет эмбеддингов для поиска");
            return new ArrayList<>();
        }

        if (allEmbeddings.size() != allChunks.size()) {
            throw new IllegalStateException(
                String.format("Несоответствие количества: embeddings=%d, chunks=%d",
                    allEmbeddings.size(), allChunks.size())
            );
        }

        // Вычислить similarity scores для всех чанков
        List<RetrievalResult> results = new ArrayList<>();

        for (int i = 0; i < allEmbeddings.size(); i++) {
            Embedding embedding = allEmbeddings.get(i);
            Chunk chunk = allChunks.get(i);

            // Вычислить cosine similarity
            float score = CosineSimilarity.compute(queryEmbedding, embedding.getVector());

            // Создать result
            RetrievalResult result = new RetrievalResult(chunk, score, 0); // rank will be set later
            results.add(result);
        }

        // Сортировать по similarity score (по убыванию)
        results.sort(Comparator.comparing(RetrievalResult::getSimilarityScore).reversed());

        // Взять top-K и установить rank
        List<RetrievalResult> topKResults = new ArrayList<>();
        int limit = Math.min(topK, results.size());

        for (int i = 0; i < limit; i++) {
            RetrievalResult result = results.get(i);
            // Создать новый result с правильным rank (1-based)
            RetrievalResult rankedResult = new RetrievalResult(
                result.getChunk(),
                result.getSimilarityScore(),
                i + 1
            );
            topKResults.add(rankedResult);
        }

        logger.info("Retrieval завершен: найдено {} чанков из {} доступных",
            topKResults.size(), allEmbeddings.size());

        if (!topKResults.isEmpty()) {
            logger.debug("Top-3 similarity scores: [{}, {}, {}]",
                topKResults.size() > 0 ? String.format("%.4f", topKResults.get(0).getSimilarityScore()) : "N/A",
                topKResults.size() > 1 ? String.format("%.4f", topKResults.get(1).getSimilarityScore()) : "N/A",
                topKResults.size() > 2 ? String.format("%.4f", topKResults.get(2).getSimilarityScore()) : "N/A"
            );
        }

        return topKResults;
    }

    /**
     * Загружает все чанки из всех документов в хранилище.
     */
    private List<Chunk> loadAllChunks() throws IOException {
        List<Chunk> allChunks = new ArrayList<>();

        // Получить все document IDs
        var index = storage.getDocumentIndex();

        for (String docId : index.keySet()) {
            try {
                List<Chunk> docChunks = storage.loadChunks(docId);
                allChunks.addAll(docChunks);
            } catch (IOException e) {
                logger.warn("Не удалось загрузить чанки для документа {}: {}",
                    docId, e.getMessage());
            }
        }

        logger.debug("Загружено {} чанков из {} документов",
            allChunks.size(), index.size());

        return allChunks;
    }

    /**
     * Проверяет, есть ли данные в хранилище для поиска.
     */
    public boolean hasData() {
        try {
            return storage.getDocumentCount() > 0;
        } catch (Exception e) {
            logger.error("Ошибка проверки наличия данных: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Возвращает количество доступных чанков для поиска.
     */
    public int getAvailableChunkCount() {
        try {
            return storage.getTotalChunkCount();
        } catch (Exception e) {
            logger.error("Ошибка получения количества чанков: {}", e.getMessage());
            return 0;
        }
    }
}
