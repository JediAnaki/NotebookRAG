package com.notebookrag.cli;

import com.notebookrag.chunking.ChunkingService;
import com.notebookrag.chunking.FixedSizeChunker;
import com.notebookrag.config.Configuration;
import com.notebookrag.domain.Chunk;
import com.notebookrag.domain.ChunkingStrategy;
import com.notebookrag.domain.Document;
import com.notebookrag.domain.DocumentType;
import com.notebookrag.domain.Embedding;
import com.notebookrag.embedding.EmbeddingService;
import com.notebookrag.embedding.OpenAIEmbeddingClient;
import com.notebookrag.ingestion.DocxDocumentParser;
import com.notebookrag.ingestion.DocumentIngestionService;
import com.notebookrag.ingestion.PdfDocumentParser;
import com.notebookrag.ingestion.TextDocumentParser;
import com.notebookrag.storage.SerializationStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI команда для загрузки и обработки документов.
 *
 * Этот класс реализует полный RAG ingestion pipeline:
 * 1. Загрузка документа (TXT/PDF/DOCX)
 * 2. Извлечение текста
 * 3. Разбиение на чанки
 * 4. Генерация эмбеддингов
 * 5. Сохранение в storage
 *
 * Пример использования:
 * ```
 * notebook-rag ingest guide.pdf
 * notebook-rag ingest tutorial.txt --chunk-size 600 --overlap 100
 * ```
 */
@Command(
    name = "ingest",
    description = "Загружает документ, разбивает на чанки и генерирует эмбеддинги",
    mixinStandardHelpOptions = true
)
public class IngestCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(IngestCommand.class);

    @Parameters(
        index = "0",
        description = "Путь к документу (TXT, PDF, DOCX)"
    )
    private String filePath;

    @Option(
        names = {"--chunk-size"},
        description = "Размер чанка в символах (по умолчанию: ${DEFAULT-VALUE})",
        defaultValue = "500"
    )
    private int chunkSize;

    @Option(
        names = {"--overlap"},
        description = "Размер перекрытия между чанками в символах (по умолчанию: ${DEFAULT-VALUE})",
        defaultValue = "50"
    )
    private int overlap;

    @Option(
        names = {"--strategy"},
        description = "Стратегия разбиения: fixed-size (по умолчанию: ${DEFAULT-VALUE})",
        defaultValue = "fixed-size"
    )
    private String strategy;

    @Option(
        names = {"-v", "--verbose"},
        description = "Детальный вывод"
    )
    private boolean verbose;

    @Override
    public Integer call() {
        try {
            // === ФАЗА 1: Инициализация сервисов ===
            if (verbose) {
                System.out.println("=== Инициализация RAG пайплайна ===\n");
            }

            logger.info("Начало ingestion для файла: {}", filePath);

            // Инициализация Document Ingestion Service
            DocumentIngestionService ingestionService = new DocumentIngestionService();
            ingestionService.registerParser(DocumentType.TXT, new TextDocumentParser());
            ingestionService.registerParser(DocumentType.PDF, new PdfDocumentParser());
            ingestionService.registerParser(DocumentType.DOCX, new DocxDocumentParser());

            // Инициализация Chunking Service
            ChunkingService chunkingService = new ChunkingService();
            chunkingService.registerChunker(ChunkingStrategy.FIXED_SIZE, new FixedSizeChunker());

            // Инициализация Embedding Service
            OpenAIEmbeddingClient embeddingClient = new OpenAIEmbeddingClient();
            EmbeddingService embeddingService = new EmbeddingService(
                embeddingClient,
                Configuration.getEmbeddingModel(),
                Configuration.getEmbeddingDimensions()
            );

            // Инициализация Storage
            SerializationStorage storage = new SerializationStorage();

            // === ФАЗА 2: Загрузка документа ===
            System.out.println("Загрузка документа: " + filePath);
            showProgressBar("Извлечение текста", 0);

            Document document;
            try {
                document = ingestionService.loadDocument(filePath);
                showProgressBar("Извлечение текста", 100);
                System.out.println(" ✓\n");

                if (verbose) {
                    System.out.println("Документ загружен:");
                    System.out.println("  ID: " + document.getId());
                    System.out.println("  Тип: " + document.getType());
                    System.out.println("  Размер: " + formatBytes(document.getSizeBytes()));
                    System.out.println("  Символов: " + document.getTextContent().length());
                    System.out.println();
                }
            } catch (Exception e) {
                System.err.println("\nОШИБКА: " + e.getMessage());
                logger.error("Ошибка загрузки документа", e);
                return 1; // Exit code: File error
            }

            // === ФАЗА 3: Разбиение на чанки ===
            System.out.print("Разбиение на чанки... ");

            List<Chunk> chunks;
            try {
                ChunkingStrategy chunkStrategy = parseStrategy(strategy);
                chunks = chunkingService.chunkDocument(
                    document,
                    chunkStrategy,
                    chunkSize,
                    overlap
                );

                System.out.println(chunks.size() + " чанков создано ✓\n");

                if (verbose) {
                    System.out.println("Параметры разбиения:");
                    System.out.println("  Стратегия: " + chunkStrategy);
                    System.out.println("  Размер чанка: " + chunkSize + " символов");
                    System.out.println("  Перекрытие: " + overlap + " символов");
                    System.out.println("  Чанков создано: " + chunks.size());
                    System.out.println();
                }
            } catch (Exception e) {
                System.err.println("\nОШИБКА: " + e.getMessage());
                logger.error("Ошибка разбиения на чанки", e);
                return 3; // Exit code: Chunking error
            }

            // === ФАЗА 4: Генерация эмбеддингов ===
            System.out.println("Генерация эмбеддингов:");

            List<Embedding> embeddings;
            try {
                long startTime = System.currentTimeMillis();

                embeddings = embeddingService.generateEmbeddingsForChunks(chunks);

                long duration = System.currentTimeMillis() - startTime;

                // Показываем прогресс
                for (int i = 0; i < chunks.size(); i += Math.max(1, chunks.size() / 20)) {
                    int progress = (i + 1) * 100 / chunks.size();
                    showProgressBar("Прогресс", progress);
                    try {
                        Thread.sleep(10); // Для визуального эффекта
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                showProgressBar("Прогресс", 100);
                System.out.println(" ✓\n");

                if (verbose) {
                    System.out.println("Эмбеддинги сгенерированы:");
                    System.out.println("  Модель: " + embeddingService.getModel());
                    System.out.println("  Размерность: " + embeddingService.getDimensions());
                    System.out.println("  Количество: " + embeddings.size());
                    System.out.println("  Время: " + formatDuration(duration));
                    System.out.println("  Среднее время на чанк: " +
                        (duration / embeddings.size()) + " мс");
                    System.out.println();
                }
            } catch (Exception e) {
                System.err.println("\nОШИБКА: " + e.getMessage());
                logger.error("Ошибка генерации эмбеддингов", e);
                return 4; // Exit code: API error
            }

            // === ФАЗА 5: Сохранение ===
            System.out.print("Сохранение... ");

            try {
                storage.saveDocument(document, chunks, embeddings);
                String storagePath = Configuration.getStorageBasePath() + "/" + document.getId() + "/";
                System.out.println("✓\n");

                // === ИТОГОВАЯ ИНФОРМАЦИЯ ===
                System.out.println("Документ успешно обработан:");
                System.out.println("  ID: " + document.getId());
                System.out.println("  Файл: " + new File(filePath).getName());
                System.out.println("  Чанков: " + chunks.size());
                System.out.println("  Путь хранения: " + storagePath);

                logger.info("Документ {} успешно обработан: {} чанков, {} эмбеддингов",
                    document.getId(), chunks.size(), embeddings.size());

                return 0; // Success

            } catch (Exception e) {
                System.err.println("\nОШИБКА: " + e.getMessage());
                logger.error("Ошибка сохранения", e);
                return 5; // Exit code: Storage error
            }

        } catch (Exception e) {
            System.err.println("\nНеожиданная ошибка: " + e.getMessage());
            logger.error("Неожиданная ошибка в IngestCommand", e);
            return 99; // Exit code: Unknown error
        }
    }

    /**
     * Парсит строковое представление стратегии разбиения.
     */
    private ChunkingStrategy parseStrategy(String strategyName) {
        if ("fixed-size".equalsIgnoreCase(strategyName)) {
            return ChunkingStrategy.FIXED_SIZE;
        }
        throw new IllegalArgumentException("Неизвестная стратегия: " + strategyName);
    }

    /**
     * Отображает прогресс-бар в консоли.
     */
    private void showProgressBar(String label, int percent) {
        int barLength = 20;
        int filled = (percent * barLength) / 100;

        StringBuilder bar = new StringBuilder();
        bar.append("\r").append(label).append(": ");
        bar.append(String.format("%3d%%", percent)).append(" [");

        for (int i = 0; i < barLength; i++) {
            if (i < filled) {
                bar.append("=");
            } else if (i == filled) {
                bar.append(">");
            } else {
                bar.append(" ");
            }
        }

        bar.append("]");
        System.out.print(bar);
    }

    /**
     * Форматирует размер файла в человеко-читаемый вид.
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Форматирует длительность в человеко-читаемый вид.
     */
    private String formatDuration(long milliseconds) {
        if (milliseconds < 1000) {
            return milliseconds + " мс";
        } else {
            return String.format("%.1f сек", milliseconds / 1000.0);
        }
    }
}
