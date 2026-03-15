package com.notebookrag.cli;

import com.notebookrag.domain.Document;
import com.notebookrag.storage.DocumentMetadataService;
import picocli.CommandLine.Command;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Команда для отображения статистики и списка загруженных документов.
 *
 * Показывает метаданные всех документов в хранилище:
 * - ID документа
 * - Путь к файлу
 * - Тип (TXT, PDF, DOCX)
 * - Размер файла
 * - Количество чанков
 * - Дата загрузки
 */
@Command(name = "list-documents",
         description = "Показать список всех загруженных документов",
         aliases = {"list-docs", "ls"},
         mixinStandardHelpOptions = true)
public class StatsCommand implements Callable<Integer> {

    private final DocumentMetadataService metadataService;

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public StatsCommand() {
        this.metadataService = new DocumentMetadataService();
    }

    @Override
    public Integer call() throws Exception {
        try {
            // Загрузить все документы
            List<Document> documents = metadataService.listAllDocuments();

            if (documents.isEmpty()) {
                System.out.println("=== Загруженные документы ===");
                System.out.println();
                System.out.println("(нет загруженных документов)");
                System.out.println();
                System.out.println("Используйте 'notebook-rag ingest <file>' для загрузки документов.");
                return 0;
            }

            // Сортировать по дате загрузки (новые первые)
            documents.sort(Comparator.comparing(Document::getUploadedAt).reversed());

            System.out.println("=== Загруженные документы ===");
            System.out.println();

            // Заголовок таблицы
            System.out.printf("%-10s | %-25s | %-4s | %-8s | %-7s | %-16s%n",
                "ID", "Файл", "Тип", "Размер", "Чанков", "Загружен");
            System.out.println("-".repeat(90));

            // Вывести документы
            for (Document doc : documents) {
                String shortId = shortenId(doc.getId());
                String fileName = extractFileName(doc.getFilePath());
                String fileType = doc.getType().name();
                String fileSize = formatFileSize(doc.getSizeBytes());
                int chunkCount = doc.getChunkCount();
                String uploadDate = doc.getUploadedAt().format(DATE_FORMATTER);

                System.out.printf("%-10s | %-25s | %-4s | %-8s | %-7d | %-16s%n",
                    shortId, truncate(fileName, 25), fileType, fileSize, chunkCount, uploadDate);
            }

            System.out.println();
            System.out.println("[" + documents.size() + " " +
                pluralize(documents.size(), "документ", "документа", "документов") + " найдено]");

            // Статистика
            int totalChunks = documents.stream()
                .mapToInt(Document::getChunkCount)
                .sum();

            long totalSize = documents.stream()
                .mapToLong(Document::getSizeBytes)
                .sum();

            System.out.println();
            System.out.println("Общая статистика:");
            System.out.println("  Документов: " + documents.size());
            System.out.println("  Чанков: " + totalChunks);
            System.out.println("  Размер: " + formatFileSize(totalSize));

            return 0;

        } catch (Exception e) {
            System.err.println("ОШИБКА: Не удалось загрузить список документов");
            System.err.println("\nДетали: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Сокращает ID до первых 8 символов для компактного вывода.
     */
    private String shortenId(String id) {
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    /**
     * Извлекает имя файла из полного пути.
     */
    private String extractFileName(String filePath) {
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    /**
     * Форматирует размер файла в читаемый вид (KB, MB, GB).
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return new DecimalFormat("#.#").format(bytes / 1024.0) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return new DecimalFormat("#.#").format(bytes / (1024.0 * 1024.0)) + " MB";
        } else {
            return new DecimalFormat("#.#").format(bytes / (1024.0 * 1024.0 * 1024.0)) + " GB";
        }
    }

    /**
     * Обрезает строку до указанной длины.
     */
    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Правильная форма множественного числа для русского языка.
     */
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
