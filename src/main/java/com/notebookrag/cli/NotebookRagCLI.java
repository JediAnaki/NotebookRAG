package com.notebookrag.cli;

import com.notebookrag.config.Configuration;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Главный CLI класс для NotebookRAG приложения.
 *
 * Использует picocli framework для обработки команд и параметров.
 *
 * Поддерживаемые команды:
 * - ingest: загрузка и обработка документа
 * - query: задать вопрос
 * - list-chunks: показать список чанков
 * - inspect-chunks: инспектировать конкретный чанк
 * - chunk-stats: статистика по чанкам
 * - show-boundaries: показать границы чанков
 * - list-documents: показать все документы
 *
 * Глобальные опции:
 * - --verbose: детальный вывод
 * - --help: показать помощь
 * - --version: показать версию
 *
 * Пример использования:
 * notebook-rag ingest document.pdf
 * notebook-rag query "Что такое Java?"
 * notebook-rag list-chunks document.pdf
 */
@Command(
    name = "notebook-rag",
    description = "NotebookRAG - образовательный RAG пайплайн на Java",
    mixinStandardHelpOptions = true,
    version = {
        "NotebookRAG v1.0.0",
        "Java RAG Pipeline для обучения",
        "JDK: ${java.version}"
    },
    subcommands = {
        // Команды будут добавлены в следующих фазах:
        // IngestCommand.class,        // Phase 3 (User Story 1)
        // QueryCommand.class,          // Phase 5 (User Story 3)
        // InspectCommand.class,        // Phase 4 (User Story 2)
        // StatsCommand.class           // Phase 7 (Polish)
    }
)
public class NotebookRagCLI implements Runnable {

    @Option(
        names = {"-v", "--verbose"},
        description = "Детальный вывод (DEBUG уровень логирования)"
    )
    private boolean verbose;

    @Option(
        names = {"--config"},
        description = "Путь к конфигурационному файлу",
        paramLabel = "<file>"
    )
    private String configFile;

    /**
     * Главная команда (когда запускается без подкоманд).
     * Показывает welcome message и подсказку.
     */
    @Override
    public void run() {
        // Инициализация конфигурации
        Configuration.initialize();

        System.out.println(getWelcomeMessage());
        System.out.println();
        System.out.println("Используйте --help для просмотра доступных команд.");
        System.out.println();
        System.out.println("Примеры:");
        System.out.println("  notebook-rag ingest document.pdf");
        System.out.println("  notebook-rag query \"Что такое Java?\"");
        System.out.println("  notebook-rag list-chunks document.pdf");
    }

    /**
     * Welcome message для приложения.
     */
    private String getWelcomeMessage() {
        String appName = Configuration.getAppName();
        String appVersion = Configuration.getAppVersion();
        String appDescription = Configuration.getAppDescription();

        return String.format(
            """

            ╔═══════════════════════════════════════════════╗
            ║  %s v%s                         ║
            ║  %s                    ║
            ║                                               ║
            ║  Образовательный RAG пайплайн                 ║
            ║  Учитесь создавать RAG системы на Java!       ║
            ╚═══════════════════════════════════════════════╝
            """,
            appName, appVersion, appDescription
        );
    }

    /**
     * Точка входа в приложение.
     */
    public static void main(String[] args) {
        // Создать CommandLine и выполнить
        CommandLine cmd = new CommandLine(new NotebookRagCLI());

        // Настроить вывод ошибок
        cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            // Красивый вывод ошибок
            System.err.println();
            System.err.println("ОШИБКА: " + ex.getMessage());
            System.err.println();

            if (ex.getCause() != null) {
                System.err.println("Детали: " + ex.getCause().getMessage());
                System.err.println();
            }

            // Показать stack trace если --verbose
            if (parseResult.hasMatchedOption("--verbose") ||
                parseResult.hasMatchedOption("-v")) {
                ex.printStackTrace(System.err);
            }

            return 1; // Exit code
        });

        // Выполнить команду
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}
