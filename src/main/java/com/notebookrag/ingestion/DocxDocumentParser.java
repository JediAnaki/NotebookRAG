package com.notebookrag.ingestion;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Парсер для документов Microsoft Word (.docx).
 *
 * Использует Apache POI для извлечения текста из DOCX файлов.
 * Извлекает текст из параграфов и таблиц, сохраняя структуру документа.
 *
 * Важно для RAG: Сохранение структуры помогает поддерживать контекст
 * при разбиении на чанки.
 */
public class DocxDocumentParser implements DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(DocxDocumentParser.class);

    @Override
    public String parse(File file) throws IOException {
        logger.debug("Парсинг DOCX файла: {}", file.getName());

        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {

            StringBuilder content = new StringBuilder();

            // Извлекаем текст из параграфов
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    content.append(text).append("\n");
                }
            }

            // Извлекаем текст из таблиц
            List<XWPFTable> tables = document.getTables();
            for (XWPFTable table : tables) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.trim().isEmpty()) {
                            content.append(cellText).append(" ");
                        }
                    }
                    content.append("\n");
                }
            }

            String result = content.toString().trim();

            if (result.isEmpty()) {
                throw new IOException("DOCX файл не содержит текста");
            }

            logger.debug("Извлечено {} символов из DOCX файла ({} параграфов, {} таблиц)",
                result.length(), paragraphs.size(), tables.size());

            return result;

        } catch (IOException e) {
            logger.error("Ошибка парсинга DOCX файла: {}", file.getName(), e);
            throw new IOException("Не удалось извлечь текст из DOCX: " + e.getMessage(), e);
        }
    }
}
