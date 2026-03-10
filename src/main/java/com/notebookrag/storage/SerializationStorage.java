package com.notebookrag.storage;

import com.notebookrag.config.Configuration;
import com.notebookrag.domain.Chunk;
import com.notebookrag.domain.Document;
import com.notebookrag.domain.Embedding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Реализация VectorStorageService через Java сериализацию.
 *
 * Сохраняет данные в .ser файлы в файловой системе.
 * Структура директорий:
 *
 * data/vectors/
 * ├── {doc-id-1}/
 * │   ├── metadata.ser     (Document)
 * │   ├── chunks.ser       (List<Chunk>)
 * │   └── embeddings.ser   (List<Embedding>)
 * ├── {doc-id-2}/
 * │   └── ...
 * └── index.ser            (Map<String, DocumentMetadata>)
 *
 * Это простая, образовательная реализация для Phase 1.
 * В будущих фазах может быть заменена на Apache Lucene или векторную БД.
 */
public class SerializationStorage implements VectorStorageService {
    private static final Logger logger = LoggerFactory.getLogger(SerializationStorage.class);

    private final Path baseStoragePath;
    private final Path indexPath;

    // Названия файлов
    private static final String METADATA_FILE = "metadata.ser";
    private static final String CHUNKS_FILE = "chunks.ser";
    private static final String EMBEDDINGS_FILE = "embeddings.ser";
    private static final String INDEX_FILE = "index.ser";

    /**
     * Метаданные документа для индекса.
     */
    private static class DocumentMetadata implements Serializable {
        private static final long serialVersionUID = 1L;
        String id;
        String filePath;
        int chunkCount;

        DocumentMetadata(String id, String filePath, int chunkCount) {
            this.id = id;
            this.filePath = filePath;
            this.chunkCount = chunkCount;
        }
    }

    /**
     * Конструктор. Инициализирует storage и создает базовую директорию.
     */
    public SerializationStorage() {
        String basePath = Configuration.getStorageBasePath();
        this.baseStoragePath = Paths.get(basePath);
        this.indexPath = baseStoragePath.resolve(INDEX_FILE);

        try {
            Files.createDirectories(baseStoragePath);
            logger.info("Storage инициализирован: {}", baseStoragePath.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать storage директорию: " + basePath, e);
        }
    }

    /**
     * Конструктор с кастомным путем (для тестирования).
     */
    public SerializationStorage(String customBasePath) {
        this.baseStoragePath = Paths.get(customBasePath);
        this.indexPath = baseStoragePath.resolve(INDEX_FILE);

        try {
            Files.createDirectories(baseStoragePath);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать storage директорию: " + customBasePath, e);
        }
    }

    @Override
    public void saveDocument(Document document, List<Chunk> chunks, List<Embedding> embeddings)
        throws IOException {

        // Валидация
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException(
                String.format("Размеры chunks (%d) и embeddings (%d) не совпадают",
                    chunks.size(), embeddings.size())
            );
        }

        String docId = document.getId();
        Path docDir = getDocumentDirectory(docId);

        // Создать директорию для документа
        Files.createDirectories(docDir);

        // Сохранить Document metadata
        saveObject(docDir.resolve(METADATA_FILE), document);

        // Сохранить Chunks
        saveObject(docDir.resolve(CHUNKS_FILE), chunks);

        // Сохранить Embeddings
        saveObject(docDir.resolve(EMBEDDINGS_FILE), embeddings);

        // Обновить индекс
        updateIndex(docId, document.getFilePath(), chunks.size());

        logger.info("Документ сохранен: id={}, path={}, chunks={}",
            docId, document.getFilePath(), chunks.size());
    }

    @Override
    public Optional<Document> loadDocument(String documentId) throws IOException {
        Path metadataPath = getDocumentDirectory(documentId).resolve(METADATA_FILE);

        if (!Files.exists(metadataPath)) {
            return Optional.empty();
        }

        Document document = loadObject(metadataPath);
        return Optional.of(document);
    }

    @Override
    public List<Chunk> loadChunks(String documentId) throws IOException {
        Path chunksPath = getDocumentDirectory(documentId).resolve(CHUNKS_FILE);

        if (!Files.exists(chunksPath)) {
            throw new IOException("Chunks file не найден для документа: " + documentId);
        }

        return loadObject(chunksPath);
    }

    @Override
    public List<Embedding> loadEmbeddings(String documentId) throws IOException {
        Path embeddingsPath = getDocumentDirectory(documentId).resolve(EMBEDDINGS_FILE);

        if (!Files.exists(embeddingsPath)) {
            throw new IOException("Embeddings file не найден для документа: " + documentId);
        }

        return loadObject(embeddingsPath);
    }

    @Override
    public List<Embedding> loadAllEmbeddings() throws IOException {
        List<Embedding> allEmbeddings = new ArrayList<>();

        // Получить все document IDs из индекса
        Map<String, DocumentMetadata> index = loadIndex();

        for (String docId : index.keySet()) {
            try {
                List<Embedding> docEmbeddings = loadEmbeddings(docId);
                allEmbeddings.addAll(docEmbeddings);
            } catch (IOException e) {
                logger.warn("Не удалось загрузить embeddings для документа {}: {}",
                    docId, e.getMessage());
            }
        }

        logger.debug("Загружено {} embeddings из {} документов",
            allEmbeddings.size(), index.size());

        return allEmbeddings;
    }

    @Override
    public List<Document> loadAllDocuments() throws IOException {
        List<Document> allDocuments = new ArrayList<>();

        // Получить все document IDs из индекса
        Map<String, DocumentMetadata> index = loadIndex();

        for (String docId : index.keySet()) {
            try {
                Optional<Document> doc = loadDocument(docId);
                doc.ifPresent(allDocuments::add);
            } catch (IOException e) {
                logger.warn("Не удалось загрузить документ {}: {}", docId, e.getMessage());
            }
        }

        logger.debug("Загружено {} документов", allDocuments.size());

        return allDocuments;
    }

    @Override
    public boolean documentExists(String documentId) {
        Path docDir = getDocumentDirectory(documentId);
        return Files.exists(docDir) && Files.isDirectory(docDir);
    }

    @Override
    public void deleteDocument(String documentId) throws IOException {
        Path docDir = getDocumentDirectory(documentId);

        if (!Files.exists(docDir)) {
            logger.warn("Попытка удалить несуществующий документ: {}", documentId);
            return;
        }

        // Удалить все файлы в директории документа
        try (Stream<Path> files = Files.list(docDir)) {
            files.forEach(file -> {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    logger.error("Не удалось удалить файл {}: {}", file, e.getMessage());
                }
            });
        }

        // Удалить директорию
        Files.delete(docDir);

        // Удалить из индекса
        removeFromIndex(documentId);

        logger.info("Документ удален: {}", documentId);
    }

    @Override
    public int getDocumentCount() {
        try {
            return loadIndex().size();
        } catch (IOException e) {
            logger.error("Ошибка чтения индекса: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public int getTotalChunkCount() {
        try {
            Map<String, DocumentMetadata> index = loadIndex();
            return index.values().stream()
                .mapToInt(meta -> meta.chunkCount)
                .sum();
        } catch (IOException e) {
            logger.error("Ошибка чтения индекса: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public void clearAll() throws IOException {
        // Удалить все директории документов
        try (Stream<Path> dirs = Files.list(baseStoragePath)) {
            dirs.filter(Files::isDirectory)
                .forEach(dir -> {
                    try {
                        deleteDirectoryRecursively(dir);
                    } catch (IOException e) {
                        logger.error("Не удалось удалить директорию {}: {}",
                            dir, e.getMessage());
                    }
                });
        }

        // Удалить индекс
        if (Files.exists(indexPath)) {
            Files.delete(indexPath);
        }

        logger.info("Storage полностью очищен");
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Возвращает путь к директории документа.
     */
    private Path getDocumentDirectory(String documentId) {
        return baseStoragePath.resolve(documentId);
    }

    /**
     * Сохраняет объект в файл через сериализацию.
     */
    private void saveObject(Path path, Object object) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(
            new BufferedOutputStream(new FileOutputStream(path.toFile())))) {
            oos.writeObject(object);
        }
    }

    /**
     * Загружает объект из файла через десериализацию.
     */
    @SuppressWarnings("unchecked")
    private <T> T loadObject(Path path) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(
            new BufferedInputStream(new FileInputStream(path.toFile())))) {
            return (T) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Ошибка десериализации: " + e.getMessage(), e);
        }
    }

    /**
     * Загружает индекс документов.
     */
    private Map<String, DocumentMetadata> loadIndex() throws IOException {
        if (!Files.exists(indexPath)) {
            return new HashMap<>();
        }

        return loadObject(indexPath);
    }

    /**
     * Загружает публичный индекс документов (docId -> filePath).
     * Используется для поиска документов по пути.
     */
    public Map<String, String> getDocumentIndex() throws IOException {
        Map<String, DocumentMetadata> internalIndex = loadIndex();
        Map<String, String> publicIndex = new HashMap<>();

        for (Map.Entry<String, DocumentMetadata> entry : internalIndex.entrySet()) {
            publicIndex.put(entry.getKey(), entry.getValue().filePath);
        }

        return publicIndex;
    }

    /**
     * Обновляет индекс, добавляя или обновляя документ.
     */
    private void updateIndex(String docId, String filePath, int chunkCount) throws IOException {
        Map<String, DocumentMetadata> index = loadIndex();
        index.put(docId, new DocumentMetadata(docId, filePath, chunkCount));
        saveObject(indexPath, index);
    }

    /**
     * Удаляет документ из индекса.
     */
    private void removeFromIndex(String docId) throws IOException {
        Map<String, DocumentMetadata> index = loadIndex();
        index.remove(docId);
        saveObject(indexPath, index);
    }

    /**
     * Рекурсивно удаляет директорию со всем содержимым.
     */
    private void deleteDirectoryRecursively(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        logger.error("Не удалось удалить {}: {}", path, e.getMessage());
                    }
                });
        }
    }
}
