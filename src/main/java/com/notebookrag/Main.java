package com.notebookrag;

import com.notebookrag.cli.NotebookRagCLI;
import picocli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Главная точка входа приложения NotebookRAG.
 *
 * NotebookRAG - это образовательный RAG (Retrieval-Augmented Generation) пайплайн
 * для понимания того, как работает векторный поиск и генерация ответов с помощью LLM.
 *
 * Основные этапы RAG:
 * 1. Document Ingestion - загрузка и парсинг документов (TXT/PDF/DOCX)
 * 2. Chunking - разбиение текста на фрагменты фиксированного размера
 * 3. Embedding - генерация векторных представлений через OpenAI API
 * 4. Storage - сохранение чанков и эмбеддингов на диск
 * 5. Retrieval - поиск релевантных чанков по косинусному сходству
 * 6. Generation - генерация ответа через LLM на основе найденного контекста
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Точка входа приложения.
     *
     * @param args аргументы командной строки, которые будут обработаны picocli
     */
    public static void main(String[] args) {
        logger.info("Запуск NotebookRAG...");

        try {
            // Создаем CLI и выполняем команду
            CommandLine commandLine = new CommandLine(new NotebookRagCLI());

            // Включаем цветной вывод в терминале
            commandLine.setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.ON));

            // Выполняем команду и получаем exit code
            int exitCode = commandLine.execute(args);

            // Завершаем приложение с соответствующим кодом
            System.exit(exitCode);

        } catch (Exception e) {
            logger.error("Критическая ошибка при запуске приложения: {}", e.getMessage(), e);
            System.err.println("Критическая ошибка: " + e.getMessage());
            System.exit(1);
        }
    }
}
