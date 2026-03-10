package com.notebookrag.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Конфигурация приложения NotebookRAG.
 *
 * Загружает настройки из нескольких источников с приоритетом:
 * 1. System properties (установленные через -D флаги JVM)
 * 2. Environment variables
 * 3. Пользовательский config файл (~/.notebookrag/config.properties)
 * 4. application.properties из classpath (defaults)
 *
 * Все параметры доступны через статические методы для простоты использования.
 */
public class Configuration {
    private static final Properties properties = new Properties();
    private static boolean initialized = false;

    // Пути к конфигурационным файлам
    private static final String DEFAULT_CONFIG = "application.properties";
    private static final String USER_CONFIG_DIR = System.getProperty("user.home") + "/.notebookrag";
    private static final String USER_CONFIG_FILE = USER_CONFIG_DIR + "/config.properties";

    /**
     * Инициализирует конфигурацию, загружая properties из всех источников.
     * Вызывается автоматически при первом обращении к любому методу.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        try {
            // 1. Загрузить defaults из classpath
            loadDefaultConfig();

            // 2. Загрузить пользовательский config (если есть)
            loadUserConfig();

            // 3. Переопределить из environment variables
            loadEnvironmentVariables();

            // 4. Валидировать обязательные параметры
            validateConfiguration();

            initialized = true;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации конфигурации: " + e.getMessage(), e);
        }
    }

    /**
     * Загружает default конфигурацию из application.properties в classpath.
     */
    private static void loadDefaultConfig() throws IOException {
        try (InputStream input = Configuration.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG)) {
            if (input == null) {
                throw new IOException("Не найден файл " + DEFAULT_CONFIG + " в classpath");
            }
            properties.load(input);
        }
    }

    /**
     * Загружает пользовательскую конфигурацию из ~/.notebookrag/config.properties (если существует).
     */
    private static void loadUserConfig() {
        Path userConfigPath = Paths.get(USER_CONFIG_FILE);
        if (Files.exists(userConfigPath)) {
            try (FileInputStream input = new FileInputStream(userConfigPath.toFile())) {
                Properties userProps = new Properties();
                userProps.load(input);
                // Переопределить defaults
                properties.putAll(userProps);
            } catch (IOException e) {
                System.err.println("ПРЕДУПРЕЖДЕНИЕ: Не удалось загрузить пользовательский config: " + e.getMessage());
            }
        }
    }

    /**
     * Загружает параметры из environment variables.
     * Поддерживаемые переменные:
     * - OPENAI_API_KEY
     * - NOTEBOOKRAG_CONFIG (путь к кастомному config файлу)
     * - NOTEBOOKRAG_STORAGE (путь к storage директории)
     */
    private static void loadEnvironmentVariables() {
        // OpenAI API ключ - самый важный параметр
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            properties.setProperty("openai.api.key", apiKey);
        }

        // Кастомный путь к storage
        String storagePath = System.getenv("NOTEBOOKRAG_STORAGE");
        if (storagePath != null && !storagePath.trim().isEmpty()) {
            properties.setProperty("storage.base.path", storagePath);
        }
    }

    /**
     * Валидирует обязательные параметры конфигурации.
     * Выбрасывает исключение если API ключ не установлен.
     */
    private static void validateConfiguration() {
        String apiKey = properties.getProperty("openai.api.key");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException(
                "ОШИБКА: OpenAI API key не найден\n\n" +
                "Решение: Установите OPENAI_API_KEY environment variable:\n" +
                "  export OPENAI_API_KEY=\"sk-your-key-here\"\n\n" +
                "Или добавьте в ~/.notebookrag/config.properties:\n" +
                "  openai.api.key=sk-your-key-here"
            );
        }
    }

    // ========================================
    // OpenAI API Configuration
    // ========================================

    public static String getOpenAIApiKey() {
        ensureInitialized();
        return properties.getProperty("openai.api.key");
    }

    public static String getOpenAIBaseUrl() {
        ensureInitialized();
        return properties.getProperty("openai.api.base.url", "https://api.openai.com/v1");
    }

    public static String getEmbeddingModel() {
        ensureInitialized();
        return properties.getProperty("openai.embedding.model", "text-embedding-3-small");
    }

    public static int getEmbeddingDimensions() {
        ensureInitialized();
        return Integer.parseInt(properties.getProperty("openai.embedding.dimensions", "1536"));
    }

    public static String getLLMModel() {
        ensureInitialized();
        return properties.getProperty("openai.llm.model", "gpt-4o-mini");
    }

    public static int getRequestTimeoutSeconds() {
        ensureInitialized();
        return Integer.parseInt(properties.getProperty("openai.request.timeout.seconds", "30"));
    }

    public static int getMaxRetries() {
        ensureInitialized();
        return Integer.parseInt(properties.getProperty("openai.max.retries", "3"));
    }

    // ========================================
    // Chunking Configuration
    // ========================================

    public static int getDefaultChunkSize() {
        ensureInitialized();
        return Integer.parseInt(properties.getProperty("chunking.default.size", "500"));
    }

    public static int getDefaultOverlap() {
        ensureInitialized();
        return Integer.parseInt(properties.getProperty("chunking.default.overlap", "50"));
    }

    public static int getMinChunkSize() {
        ensureInitialized();
        return Integer.parseInt(properties.getProperty("chunking.min.size", "100"));
    }

    public static int getMaxChunkSize() {
        ensureInitialized();
        return Integer.parseInt(properties.getProperty("chunking.max.size", "2000"));
    }

    // ========================================
    // Retrieval Configuration
    // ========================================

    public static int getDefaultTopK() {
        ensureInitialized();
        return Integer.parseInt(properties.getProperty("retrieval.default.topk", "3"));
    }

    public static int getMinTopK() {
        ensureInitialized();
        return Integer.parseInt(properties.getProperty("retrieval.min.topk", "1"));
    }

    public static int getMaxTopK() {
        ensureInitialized();
        return Integer.parseInt(properties.getProperty("retrieval.max.topk", "100"));
    }

    public static double getSimilarityThreshold() {
        ensureInitialized();
        return Double.parseDouble(properties.getProperty("retrieval.similarity.threshold", "0.0"));
    }

    // ========================================
    // Storage Configuration
    // ========================================

    public static String getStorageBasePath() {
        ensureInitialized();
        return properties.getProperty("storage.base.path", "data/vectors");
    }

    // ========================================
    // Document Processing Configuration
    // ========================================

    public static int getMaxDocumentSizeMB() {
        ensureInitialized();
        return Integer.parseInt(properties.getProperty("document.max.size.mb", "50"));
    }

    public static String[] getSupportedDocumentTypes() {
        ensureInitialized();
        String types = properties.getProperty("document.supported.types", "txt,pdf,docx");
        return types.split(",");
    }

    // ========================================
    // Application Metadata
    // ========================================

    public static String getAppName() {
        ensureInitialized();
        return properties.getProperty("app.name", "NotebookRAG");
    }

    public static String getAppVersion() {
        ensureInitialized();
        return properties.getProperty("app.version", "1.0.0");
    }

    public static String getAppDescription() {
        ensureInitialized();
        return properties.getProperty("app.description", "Java RAG Pipeline");
    }

    // ========================================
    // Utility Methods
    // ========================================

    /**
     * Получить произвольный параметр из конфигурации.
     */
    public static String getProperty(String key) {
        ensureInitialized();
        return properties.getProperty(key);
    }

    /**
     * Получить параметр с default значением.
     */
    public static String getProperty(String key, String defaultValue) {
        ensureInitialized();
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Гарантирует что конфигурация инициализирована перед использованием.
     */
    private static void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    /**
     * Сбросить конфигурацию (используется для тестирования).
     */
    public static synchronized void reset() {
        properties.clear();
        initialized = false;
    }
}
