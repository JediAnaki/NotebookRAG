package com.notebookrag.storage;

import com.notebookrag.domain.Document;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Сервис для загрузки метаданных документов из хранилища.
 *
 * Этот класс помогает находить документы по ID или пути к файлу,
 * и предоставляет доступ к метаданным документов без необходимости
 * загружать все чанки и эмбеддинги.
 */
public class DocumentMetadataService {

    private final SerializationStorage storage;
    private final String dataPath;

    public DocumentMetadataService() {
        this.storage = new SerializationStorage();
        // TODO: получить путь из Configuration
        this.dataPath = "data/vectors/";
    }

    /**
     * Найти документ по ID или пути к файлу.
     *
     * @param idOrPath ID документа или путь к файлу
     * @return найденный документ или null
     */
    public Document findDocument(String idOrPath) throws IOException {
        // Сначала попробовать загрузить по ID
        try {
            Document doc = loadDocumentById(idOrPath);
            if (doc != null) {
                return doc;
            }
        } catch (IOException e) {
            // Игнорировать, попробуем по пути
        }

        // Попробовать найти по пути к файлу
        return findDocumentByPath(idOrPath);
    }

    /**
     * Загрузить документ по ID.
     */
    private Document loadDocumentById(String documentId) throws IOException {
        File metadataFile = new File(dataPath + documentId + "/metadata.ser");
        if (!metadataFile.exists()) {
            return null;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(metadataFile))) {
            return (Document) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Не удалось десериализовать метаданные документа", e);
        }
    }

    /**
     * Найти документ по пути к файлу.
     */
    private Document findDocumentByPath(String filePath) throws IOException {
        // Нормализовать путь
        String normalizedPath = new File(filePath).getAbsolutePath();

        // Загрузить индекс
        Map<String, String> index = storage.getDocumentIndex();

        // Поиск по всем документам
        for (Map.Entry<String, String> entry : index.entrySet()) {
            String docId = entry.getKey();
            String docPath = entry.getValue();

            // Сравнить пути
            if (new File(docPath).getAbsolutePath().equals(normalizedPath)) {
                return loadDocumentById(docId);
            }
        }

        return null;
    }

    /**
     * Получить список всех документов.
     */
    public List<Document> listAllDocuments() throws IOException {
        List<Document> documents = new ArrayList<>();
        Map<String, String> index = storage.getDocumentIndex();

        for (String docId : index.keySet()) {
            try {
                Document doc = loadDocumentById(docId);
                if (doc != null) {
                    documents.add(doc);
                }
            } catch (IOException e) {
                // Пропустить поврежденные документы
                System.err.println("Предупреждение: не удалось загрузить документ " + docId);
            }
        }

        return documents;
    }
}
