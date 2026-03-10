package com.notebookrag.domain;

/**
 * Типы поддерживаемых документов.
 *
 * NotebookRAG поддерживает три формата документов:
 * - TXT: простые текстовые файлы
 * - PDF: документы в формате Portable Document Format
 * - DOCX: Microsoft Word документы (OOXML формат)
 */
public enum DocumentType {
    /**
     * Текстовый файл (.txt)
     */
    TXT(".txt", "Текстовый файл"),

    /**
     * PDF документ (.pdf)
     */
    PDF(".pdf", "PDF документ"),

    /**
     * Microsoft Word документ (.docx)
     */
    DOCX(".docx", "Word документ");

    private final String extension;
    private final String description;

    DocumentType(String extension, String description) {
        this.extension = extension;
        this.description = description;
    }

    public String getExtension() {
        return extension;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Определяет тип документа по расширению файла.
     *
     * @param filename имя файла
     * @return тип документа
     * @throws IllegalArgumentException если расширение не поддерживается
     */
    public static DocumentType fromFilename(String filename) {
        String lowerFilename = filename.toLowerCase();
        for (DocumentType type : values()) {
            if (lowerFilename.endsWith(type.extension)) {
                return type;
            }
        }
        throw new IllegalArgumentException(
            "Неподдерживаемый формат файла: " + filename + ". " +
            "Поддерживаются: .txt, .pdf, .docx"
        );
    }

    /**
     * Проверяет, поддерживается ли расширение файла.
     *
     * @param filename имя файла
     * @return true если формат поддерживается
     */
    public static boolean isSupported(String filename) {
        try {
            fromFilename(filename);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return description + " (" + extension + ")";
    }
}
