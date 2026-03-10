package com.notebookrag.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Парсер для PDF файлов (.pdf).
 *
 * Использует Apache PDFBox для извлечения текста из PDF документов.
 * Обрабатывает PDF постранично для эффективного использования памяти.
 *
 * Важно: Некоторые PDF файлы могут содержать изображения или сканы.
 * В таких случаях текст не будет извлечен (OCR не поддерживается в базовой версии).
 */
public class PdfDocumentParser implements DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(PdfDocumentParser.class);

    @Override
    public String parse(File file) throws IOException {
        logger.debug("Парсинг PDF файла: {}", file.getName());

        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(file)) {
            int pageCount = document.getNumberOfPages();
            logger.debug("PDF содержит {} страниц", pageCount);

            // Создаем text stripper для извлечения текста
            PDFTextStripper stripper = new PDFTextStripper();

            // Извлекаем текст из всех страниц
            String content = stripper.getText(document);

            if (content == null || content.trim().isEmpty()) {
                throw new IOException(
                    "PDF файл не содержит текста (возможно, это сканированное изображение)"
                );
            }

            logger.debug("Извлечено {} символов из {} страниц PDF",
                content.length(), pageCount);

            return content;

        } catch (IOException e) {
            logger.error("Ошибка парсинга PDF файла: {}", file.getName(), e);
            throw new IOException("Не удалось извлечь текст из PDF: " + e.getMessage(), e);
        }
    }
}
