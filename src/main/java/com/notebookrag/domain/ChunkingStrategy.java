package com.notebookrag.domain;

/**
 * Стратегии разбиения документов на чанки.
 *
 * В Phase 1 поддерживается только фиксированное разбиение.
 * Будущие фазы могут добавить семантическое разбиение, разбиение по предложениям и т.д.
 */
public enum ChunkingStrategy {
    /**
     * Фиксированный размер с перекрытием.
     *
     * Разбивает текст на чанки фиксированного размера (например, 500 символов)
     * с опциональным перекрытием между соседними чанками (например, 50 символов).
     *
     * Перекрытие важно для RAG: оно предотвращает потерю контекста на границах чанков.
     * Если предложение разрезано пополам, перекрытие сохранит его целиком хотя бы
     * в одном из чанков.
     *
     * Пример с размером 500 и перекрытием 50:
     * Chunk 1: символы 0-500
     * Chunk 2: символы 450-950  (перекрытие: 450-500)
     * Chunk 3: символы 900-1400 (перекрытие: 900-950)
     */
    FIXED_SIZE("fixed-size", "Фиксированный размер с перекрытием");

    // Будущие стратегии:
    // SEMANTIC("semantic", "Семантическое разбиение") - Phase 2
    // SENTENCE_BASED("sentence", "По предложениям") - Phase 2
    // RECURSIVE("recursive", "Рекурсивное разбиение") - Phase 3

    private final String code;
    private final String description;

    ChunkingStrategy(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Получить стратегию по коду.
     *
     * @param code код стратегии (например, "fixed-size")
     * @return стратегия chunking
     * @throws IllegalArgumentException если код не распознан
     */
    public static ChunkingStrategy fromCode(String code) {
        for (ChunkingStrategy strategy : values()) {
            if (strategy.code.equalsIgnoreCase(code)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException(
            "Неизвестная стратегия chunking: " + code + ". " +
            "Поддерживается: fixed-size"
        );
    }

    @Override
    public String toString() {
        return description + " (" + code + ")";
    }
}
