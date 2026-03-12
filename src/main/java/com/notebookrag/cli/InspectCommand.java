package com.notebookrag.cli;

import com.notebookrag.domain.Chunk;
import com.notebookrag.domain.Document;
import com.notebookrag.domain.Embedding;
import com.notebookrag.storage.DocumentMetadataService;
import com.notebookrag.storage.SerializationStorage;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Команда инспектирования чанков документа.
 *
 * Позволяет просматривать чанки, их метаданные и понимать процесс разбиения.
 * Это образовательный инструмент для понимания того, как RAG система
 * разбивает документы на части.
 */
@Command(name = "inspect",
         description = "Инспектировать чанки документа",
         mixinStandardHelpOptions = true,
         subcommands = {
             InspectCommand.ListChunks.class,
             InspectCommand.InspectChunk.class,
             InspectCommand.ChunkStats.class,
             InspectCommand.ShowBoundaries.class
         })
public class InspectCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println("Используйте одну из подкоманд: list-chunks, inspect-chunk, chunk-stats, show-boundaries");
        System.out.println("Запустите 'notebook-rag inspect --help' для получения помощи");
        return 0;
    }

    /**
     * Подкоманда для отображения списка чанков.
     */
    @Command(name = "list-chunks",
             description = "Показать список чанков документа")
    static class ListChunks implements Callable<Integer> {

        @Parameters(index = "0", description = "ID документа или путь к файлу")
        private String documentIdOrPath;

        @Option(names = {"--limit"}, description = "Показать первые N чанков (default: all)")
        private Integer limit;

        @Option(names = {"--preview"}, description = "Длина превью в символах (default: 50)", defaultValue = "50")
        private int previewLength = 50;

        private final SerializationStorage storage = new SerializationStorage();
        private final DocumentMetadataService metadataService = new DocumentMetadataService();

        @Override
        public Integer call() throws Exception {
            try {
                // Найти документ по ID или пути
                Document document = metadataService.findDocument(documentIdOrPath);
                if (document == null) {
                    System.err.println("ОШИБКА: Документ не найден");
                    System.err.println("\nДетали: Документ '" + documentIdOrPath + "' не существует в хранилище.");
                    System.err.println("\nРешение: Проверьте ID документа или путь к файлу. Используйте 'list-documents' для просмотра загруженных документов.");
                    return 1;
                }

                // Загрузить чанки
                List<Chunk> chunks = storage.loadChunks(document.getId());

                System.out.println("Документ: " + document.getFilePath() + " (ID: " + document.getId() + ")");
                System.out.println("Всего чанков: " + chunks.size());
                System.out.println();

                // Заголовок таблицы
                System.out.printf("#  | Размер | Позиция    | Превью%n");
                System.out.printf("---|--------|------------|---------------------------------------------------%n");

                // Определить лимит
                int displayLimit = (limit != null) ? Math.min(limit, chunks.size()) : chunks.size();

                // Вывести чанки
                for (int i = 0; i < displayLimit; i++) {
                    Chunk chunk = chunks.get(i);
                    String preview = createPreview(chunk.getContent(), previewLength);
                    System.out.printf("%-3d| %-7d| %-11s| %s%n",
                        i + 1,
                        chunk.getSizeChars(),
                        chunk.getStartPosition() + "-" + chunk.getEndPosition(),
                        preview);
                }

                System.out.println();
                System.out.println("[" + displayLimit + " " + pluralize(displayLimit, "чанк", "чанка", "чанков") + " показано]");

                return 0;

            } catch (IOException e) {
                System.err.println("ОШИБКА: Не удалось загрузить чанки");
                System.err.println("\nДетали: " + e.getMessage());
                System.err.println("\nРешение: Проверьте, что документ был успешно обработан командой 'ingest'.");
                return 1;
            }
        }

        private String createPreview(String content, int length) {
            if (content.length() <= length) {
                return content;
            }
            return content.substring(0, length) + "...";
        }

        private String pluralize(int count, String one, String few, String many) {
            int mod10 = count % 10;
            int mod100 = count % 100;

            if (mod10 == 1 && mod100 != 11) {
                return one;
            } else if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) {
                return few;
            } else {
                return many;
            }
        }
    }

    /**
     * Подкоманда для детального просмотра одного чанка.
     */
    @Command(name = "inspect-chunk",
             description = "Показать детальную информацию о конкретном чанке")
    static class InspectChunk implements Callable<Integer> {

        @Parameters(index = "0", description = "ID документа или путь к файлу")
        private String documentIdOrPath;

        @Option(names = {"--chunk-id"}, required = true, description = "ID чанка для просмотра")
        private int chunkId;

        private final SerializationStorage storage = new SerializationStorage();
        private final DocumentMetadataService metadataService = new DocumentMetadataService();

        @Override
        public Integer call() throws Exception {
            try {
                // Найти документ
                Document document = metadataService.findDocument(documentIdOrPath);
                if (document == null) {
                    System.err.println("ОШИБКА: Документ не найден");
                    return 1;
                }

                // Загрузить чанки и эмбеддинги
                List<Chunk> chunks = storage.loadChunks(document.getId());
                List<Embedding> embeddings = storage.loadEmbeddings(document.getId());

                // Проверить, что chunk ID валиден (1-based index)
                if (chunkId < 1 || chunkId > chunks.size()) {
                    System.err.println("ОШИБКА: Чанк не найден");
                    System.err.println("\nДетали: Чанк #" + chunkId + " не существует. Документ содержит " + chunks.size() + " чанков.");
                    System.err.println("\nРешение: Используйте 'list-chunks' для просмотра доступных чанков.");
                    return 2;
                }

                // Получить чанк (convert to 0-based index)
                Chunk chunk = chunks.get(chunkId - 1);
                Embedding embedding = embeddings.get(chunkId - 1);

                // Вывести детальную информацию
                System.out.println("=== Chunk #" + chunkId + " ===");
                System.out.println();
                System.out.println("Документ: " + document.getFilePath() + " (ID: " + document.getId() + ")");
                System.out.println("Позиция: " + chunk.getStartPosition() + "-" + chunk.getEndPosition() + " (символы в исходном тексте)");
                System.out.println("Размер: " + chunk.getSizeChars() + " символов");
                System.out.println("Стратегия: " + chunk.getStrategy().getDescription());
                System.out.println("Перекрытие: " + chunk.getOverlapSize() + " символов");
                System.out.println("Создан: " + chunk.getCreatedAt());
                System.out.println();
                System.out.println("--- Содержимое ---");
                System.out.println(chunk.getContent());
                System.out.println("--- Конец содержимого ---");
                System.out.println();
                System.out.println("Embedding:");
                System.out.println("  Модель: " + embedding.getModel());
                System.out.println("  Размерность: " + embedding.getDimensions());
                System.out.println("  Первые 5 компонентов: " + formatVector(embedding.getVector(), 5));

                return 0;

            } catch (IOException e) {
                System.err.println("ОШИБКА: Не удалось загрузить данные чанка");
                System.err.println("\nДетали: " + e.getMessage());
                return 1;
            }
        }

        private String formatVector(float[] vector, int count) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < Math.min(count, vector.length); i++) {
                if (i > 0) sb.append(", ");
                sb.append(String.format("%.4f", vector[i]));
            }
            sb.append("]");
            return sb.toString();
        }
    }

    /**
     * Подкоманда для отображения агрегированной статистики по чанкам.
     */
    @Command(name = "chunk-stats",
             description = "Показать статистику по чанкам документа")
    static class ChunkStats implements Callable<Integer> {

        @Parameters(index = "0", description = "ID документа или путь к файлу")
        private String documentIdOrPath;

        private final SerializationStorage storage = new SerializationStorage();
        private final DocumentMetadataService metadataService = new DocumentMetadataService();

        @Override
        public Integer call() throws Exception {
            try {
                // Найти документ
                Document document = metadataService.findDocument(documentIdOrPath);
                if (document == null) {
                    System.err.println("ОШИБКА: Документ не найден");
                    return 1;
                }

                // Загрузить чанки
                List<Chunk> chunks = storage.loadChunks(document.getId());

                // Вычислить статистику
                int minSize = Integer.MAX_VALUE;
                int maxSize = Integer.MIN_VALUE;
                int totalSize = 0;
                int minChunk = 0;
                int maxChunk = 0;

                for (int i = 0; i < chunks.size(); i++) {
                    Chunk chunk = chunks.get(i);
                    int size = chunk.getSizeChars();
                    totalSize += size;

                    if (size < minSize) {
                        minSize = size;
                        minChunk = i + 1;
                    }
                    if (size > maxSize) {
                        maxSize = size;
                        maxChunk = i + 1;
                    }
                }

                double avgSize = chunks.isEmpty() ? 0 : (double) totalSize / chunks.size();

                // Найти медиану
                int medianSize = chunks.isEmpty() ? 0 : chunks.get(chunks.size() / 2).getSizeChars();

                // Вывести статистику
                System.out.println("=== Статистика чанков: " + document.getFilePath() + " ===");
                System.out.println();
                System.out.println("Всего чанков: " + chunks.size());

                if (!chunks.isEmpty()) {
                    System.out.println("Стратегия: " + chunks.get(0).getStrategy().getDescription());
                    System.out.println("Перекрытие: " + chunks.get(0).getOverlapSize() + " символов");
                }

                System.out.println();
                System.out.println("Распределение размеров:");
                System.out.println("  Минимум: " + minSize + " символов (chunk #" + minChunk + ")");
                System.out.println("  Максимум: " + maxSize + " символов (chunk #" + maxChunk + ")");
                System.out.println("  Среднее: " + String.format("%.0f", avgSize) + " символов");
                System.out.println("  Медиана: " + medianSize + " символов");
                System.out.println();
                System.out.println("Позиции:");
                if (!chunks.isEmpty()) {
                    System.out.println("  Начало: " + chunks.get(0).getStartPosition());
                    System.out.println("  Конец: " + chunks.get(chunks.size() - 1).getEndPosition());
                    System.out.println("  Общее покрытие: " + chunks.get(chunks.size() - 1).getEndPosition() + " символов исходного текста");
                }

                return 0;

            } catch (IOException e) {
                System.err.println("ОШИБКА: Не удалось загрузить чанки");
                System.err.println("\nДетали: " + e.getMessage());
                return 1;
            }
        }
    }

    /**
     * Подкоманда для визуализации границ чанков.
     */
    @Command(name = "show-boundaries",
             description = "Визуализировать границы чанков в тексте")
    static class ShowBoundaries implements Callable<Integer> {

        @Parameters(index = "0", description = "ID документа или путь к файлу")
        private String documentIdOrPath;

        @Option(names = {"--limit"}, description = "Показать первые N чанков (default: 5)", defaultValue = "5")
        private int limit = 5;

        @Option(names = {"--color"}, description = "Цветная подсветка границ", defaultValue = "true")
        private boolean useColor = true;

        private final SerializationStorage storage = new SerializationStorage();
        private final DocumentMetadataService metadataService = new DocumentMetadataService();

        // ANSI color codes
        private static final String ANSI_RESET = "\u001B[0m";
        private static final String ANSI_GREEN = "\u001B[32m";
        private static final String ANSI_YELLOW = "\u001B[33m";
        private static final String ANSI_BLUE = "\u001B[34m";

        @Override
        public Integer call() throws Exception {
            try {
                // Найти документ
                Document document = metadataService.findDocument(documentIdOrPath);
                if (document == null) {
                    System.err.println("ОШИБКА: Документ не найден");
                    return 1;
                }

                // Загрузить чанки
                List<Chunk> chunks = storage.loadChunks(document.getId());

                System.out.println("=== Boundaries: " + document.getFilePath() + " ===");
                System.out.println();

                int displayLimit = Math.min(limit, chunks.size());

                for (int i = 0; i < displayLimit; i++) {
                    Chunk currentChunk = chunks.get(i);

                    // Начало чанка
                    String startMarker = "[CHUNK " + (i + 1) + " START]";
                    if (useColor) {
                        System.out.println(ANSI_GREEN + startMarker + ANSI_RESET);
                    } else {
                        System.out.println(startMarker);
                    }

                    // Содержимое чанка
                    System.out.println(currentChunk.getContent());

                    // Конец чанка / начало следующего (с перекрытием)
                    if (i < chunks.size() - 1) {
                        Chunk nextChunk = chunks.get(i + 1);
                        int overlapSize = currentChunk.getOverlapSize();

                        String endMarker = "[CHUNK " + (i + 1) + " END / CHUNK " + (i + 2) + " START (overlap " + overlapSize + " chars)]";
                        if (useColor) {
                            System.out.println(ANSI_YELLOW + endMarker + ANSI_RESET);
                        } else {
                            System.out.println(endMarker);
                        }
                    } else {
                        String endMarker = "[CHUNK " + (i + 1) + " END]";
                        if (useColor) {
                            System.out.println(ANSI_BLUE + endMarker + ANSI_RESET);
                        } else {
                            System.out.println(endMarker);
                        }
                    }

                    System.out.println();
                }

                if (chunks.size() > displayLimit) {
                    System.out.println("[Показано " + displayLimit + " из " + chunks.size() + " чанков. Используйте --limit для изменения.]");
                }

                return 0;

            } catch (IOException e) {
                System.err.println("ОШИБКА: Не удалось загрузить чанки");
                System.err.println("\nДетали: " + e.getMessage());
                return 1;
            }
        }
    }
}
