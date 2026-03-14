package com.notebookrag.cli;

import com.notebookrag.domain.GeneratedResponse;
import com.notebookrag.domain.Query;
import com.notebookrag.domain.RetrievalResult;
import com.notebookrag.embedding.EmbeddingService;
import com.notebookrag.generation.GenerationService;
import com.notebookrag.retrieval.RetrievalService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Команда для выполнения RAG запроса: Query → Embed → Retrieve → Generate.
 *
 * Это основная команда пайплайна RAG, объединяющая все компоненты:
 * 1. Embedding: преобразование вопроса в вектор
 * 2. Retrieval: поиск релевантных чанков по similarity
 * 3. Generation: генерация ответа через LLM на основе найденных чанков
 *
 * Пример: notebook-rag query "Что такое Java?"
 */
@Command(name = "query",
         description = "Задать вопрос и получить ответ на основе загруженных документов",
         mixinStandardHelpOptions = true)
public class QueryCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Текст вопроса")
    private String questionText;

    @Option(names = {"--top-k"}, description = "Количество извлекаемых чанков (default: 3)", defaultValue = "3")
    private int topK = 3;

    @Option(names = {"--model"}, description = "Модель LLM (default: gpt-4o-mini)", defaultValue = "gpt-4o-mini")
    private String model = "gpt-4o-mini";

    @Option(names = {"--show-sources"}, description = "Показать использованные чанки с scores")
    private boolean showSources = false;

    @Option(names = {"-v", "--verbose"}, description = "Показать детали RAG пайплайна")
    private boolean verbose = false;

    private final EmbeddingService embeddingService;
    private final RetrievalService retrievalService;
    private final GenerationService generationService;

    public QueryCommand() {
        this.embeddingService = new EmbeddingService();
        this.retrievalService = new RetrievalService();
        this.generationService = new GenerationService();
    }

    @Override
    public Integer call() throws Exception {
        // Валидация
        if (questionText == null || questionText.trim().isEmpty()) {
            System.err.println("ОШИБКА: Пустой запрос");
            System.err.println("\nДетали: Текст запроса не может быть пустым.");
            System.err.println("\nРешение: Укажите вопрос в кавычках, например:");
            System.err.println("  notebook-rag query \"Что такое Java?\"");
            return 2;
        }

        // Проверить, что есть загруженные документы
        if (!retrievalService.hasData()) {
            System.err.println("ОШИБКА: Нет загруженных документов");
            System.err.println("\nДетали: Хранилище пустое. Невозможно выполнить поиск.");
            System.err.println("\nРешение: Сначала загрузите документы командой 'ingest':");
            System.err.println("  notebook-rag ingest document.pdf");
            return 1;
        }

        try {
            // Создать Query объект
            Query query = new Query(
                UUID.randomUUID().toString(),
                questionText,
                null, // embedding будет установлен позже
                LocalDateTime.now(),
                topK
            );

            if (verbose) {
                System.out.println("=== RAG Pipeline ===");
                System.out.println();
            }

            long totalStartTime = System.currentTimeMillis();

            // STEP 1: Embedding query
            if (verbose) {
                System.out.println("1. Embedding query...");
            }

            long embeddingStart = System.currentTimeMillis();
            float[] queryEmbedding = embeddingService.generateEmbedding(questionText);
            long embeddingEnd = System.currentTimeMillis();

            query = new Query(
                query.getId(),
                query.getText(),
                queryEmbedding,
                query.getTimestamp(),
                query.getTopK()
            );

            if (verbose) {
                System.out.println("   Модель: " + embeddingService.getModel());
                System.out.println("   Размерность: " + queryEmbedding.length);
                System.out.println("   Время: " + (embeddingEnd - embeddingStart) / 1000.0 + " сек");
                System.out.println();
            }

            // STEP 2: Retrieval
            if (verbose) {
                System.out.println("2. Retrieval (top-" + topK + ")...");
            }

            long retrievalStart = System.currentTimeMillis();
            List<RetrievalResult> retrievedChunks = retrievalService.retrieveTopK(queryEmbedding, topK);
            long retrievalEnd = System.currentTimeMillis();

            if (retrievedChunks.isEmpty()) {
                System.err.println("ОШИБКА: Не найдено релевантных чанков");
                System.err.println("\nДетали: Поиск не вернул результатов.");
                System.err.println("\nРешение: Убедитесь, что загруженные документы содержат информацию по вашему вопросу.");
                return 5;
            }

            if (verbose) {
                for (RetrievalResult result : retrievedChunks) {
                    System.out.printf("   Chunk #%d: similarity=%.4f (pos: %d-%d)%n",
                        result.getRank(),
                        result.getSimilarityScore(),
                        result.getChunk().getStartPosition(),
                        result.getChunk().getEndPosition()
                    );
                }
                System.out.println();
            }

            // STEP 3: Generation
            if (verbose) {
                System.out.println("3. Generation...");
            }

            long generationStart = System.currentTimeMillis();
            GeneratedResponse response = generationService.generateResponse(query, retrievedChunks, model);
            long generationEnd = System.currentTimeMillis();

            if (verbose) {
                System.out.println("   Модель: " + model);
                System.out.println("   Токены: " + response.getTokensUsed() +
                    " (prompt + completion)");
                System.out.println("   Время: " + response.getGenerationTimeMs() / 1000.0 + " сек");
                System.out.println();
            }

            // Вывести ответ
            if (verbose) {
                System.out.println("=== Ответ ===");
            }

            System.out.println(response.getResponseText());
            System.out.println();

            // Показать источники если запрошено
            if (showSources || verbose) {
                System.out.print("[Источники: chunks");
                for (int i = 0; i < retrievedChunks.size(); i++) {
                    System.out.print(" #" + (i + 1));
                    if (showSources) {
                        System.out.printf(" (%.4f)", retrievedChunks.get(i).getSimilarityScore());
                    }
                    if (i < retrievedChunks.size() - 1) {
                        System.out.print(",");
                    }
                }
                System.out.println("]");
            }

            if (verbose) {
                long totalEnd = System.currentTimeMillis();
                System.out.println();
                System.out.println("[Total time: " + (totalEnd - totalStartTime) / 1000.0 + " сек]");
            }

            return 0;

        } catch (Exception e) {
            System.err.println("ОШИБКА: " + e.getMessage());
            System.err.println("\nДетали: " + (e.getCause() != null ? e.getCause().getMessage() : "Неизвестная ошибка"));

            if (e.getMessage().contains("API")) {
                System.err.println("\nРешение: Проверьте OPENAI_API_KEY и доступность OpenAI API.");
                return 4;
            }

            if (verbose) {
                e.printStackTrace();
            }

            return 1;
        }
    }
}
