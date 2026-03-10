package com.notebookrag.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Парсер для текстовых файлов (.txt).
 *
 * Самый простой парсер - просто читает содержимое файла как текст.
 * Использует UTF-8 кодировку по умолчанию.
 */
public class TextDocumentParser implements DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(TextDocumentParser.class);

    @Override
    public String parse(File file) throws IOException {
        logger.debug("Парсинг TXT файла: {}", file.getName());

        try {
            // Читаем весь файл как строку в UTF-8
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);

            logger.debug("Извлечено {} символов из TXT файла", content.length());
            return content;

        } catch (IOException e) {
            logger.error("Ошибка чтения TXT файла: {}", file.getName(), e);
            throw new IOException("Не удалось прочитать TXT файл: " + e.getMessage(), e);
        }
    }
}
