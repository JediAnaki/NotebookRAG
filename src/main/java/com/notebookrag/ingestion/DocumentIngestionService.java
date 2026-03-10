package com.notebookrag.ingestion;

import com.notebookrag.domain.Document;
import com.notebookrag.domain.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис для загрузки и обработки документов.
 *
 * Этот компонент отвечает за первый этап RAG пайплайна - загрузку документов
 * и извлечение текстового содержимого из различных форматов файлов (TXT, PDF, DOCX).
 *
 * Для каждого типа документа используется специализированный парсер,
 * который знает как извлечь текст из конкретного формата.
 */
public class DocumentIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final Map<DocumentType, DocumentParser> parsers;

    public DocumentIngestionService() {
        this.parsers = new HashMap<>();
    }

    /**
     * Регистрирует парсер для конкретного типа документа.
     *
     * @param type тип документа (TXT, PDF, DOCX)
     * @param parser реализация парсера для этого типа
     */
    public void registerParser(DocumentType type, DocumentParser parser) {
        parsers.put(type, parser);
        logger.debug("Зарегистрирован парсер для типа: {}", type);
    }

    /**
     * Загружает документ из файла и извлекает текстовое содержимое.
     *
     * Процесс:
     * 1. Проверка существования файла
     * 2. Определение типа документа по расширению
     * 3. Валидация размера файла
     * 4. Выбор подходящего парсера
     * 5. Извлечение текста из документа
     *
     * @param filePath путь к файлу документа
     * @return объект Document с извлеченным текстом и метаданными
     * @throws IOException если файл не найден или не может быть прочитан
     * @throws UnsupportedOperationException если формат файла не поддерживается
     */
    public Document loadDocument(String filePath) throws IOException {
        logger.info("Начало загрузки документа: {}", filePath);

        // Проверка существования файла
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("Файл не найден: " + filePath);
        }

        if (!file.isFile()) {
            throw new IOException("Путь не является файлом: " + filePath);
        }

        // Определение типа документа
        DocumentType type = detectDocumentType(filePath);
        logger.debug("Определен тип документа: {}", type);

        // Валидация размера файла (максимум 50MB по умолчанию)
        long maxSize = 50 * 1024 * 1024; // 50MB
        if (file.length() > maxSize) {
            throw new IOException(String.format(
                "Размер файла (%d bytes) превышает максимально допустимый (%d bytes)",
                file.length(), maxSize
            ));
        }

        // Получение парсера для типа документа
        DocumentParser parser = parsers.get(type);
        if (parser == null) {
            throw new UnsupportedOperationException(
                "Парсер для типа " + type + " не зарегистрирован"
            );
        }

        // Извлечение текста
        logger.debug("Извлечение текста из документа...");
        String textContent = parser.parse(file);

        if (textContent == null || textContent.trim().isEmpty()) {
            throw new IOException("Не удалось извлечь текст из документа (пустое содержимое)");
        }

        // Создание объекта Document
        Document document = new Document(filePath, type, file.length());
        document.setTextContent(textContent);
        document.setState(Document.ProcessingState.PARSED);

        logger.info("Документ успешно загружен: {} (ID: {}, размер: {} символов)",
            filePath, document.getId(), textContent.length());

        return document;
    }

    /**
     * Определяет тип документа по расширению файла.
     *
     * @param filePath путь к файлу
     * @return тип документа
     * @throws UnsupportedOperationException если расширение не поддерживается
     */
    private DocumentType detectDocumentType(String filePath) {
        String lowerPath = filePath.toLowerCase();

        if (lowerPath.endsWith(".txt")) {
            return DocumentType.TXT;
        } else if (lowerPath.endsWith(".pdf")) {
            return DocumentType.PDF;
        } else if (lowerPath.endsWith(".docx")) {
            return DocumentType.DOCX;
        } else {
            throw new UnsupportedOperationException(
                "Неподдерживаемый формат файла. Поддерживаются: .txt, .pdf, .docx"
            );
        }
    }
}
