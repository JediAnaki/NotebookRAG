package com.notebookrag.retrieval;

/**
 * Утилита для вычисления косинусного сходства между векторами.
 *
 * Косинусное сходство - стандартная метрика для сравнения векторов embeddings.
 * Она измеряет косинус угла между двумя векторами в многомерном пространстве.
 *
 * Формула:
 * similarity(A, B) = (A · B) / (||A|| * ||B||)
 *
 * где:
 * - A · B = скалярное произведение векторов
 * - ||A|| = норма (длина) вектора A
 * - ||B|| = норма вектора B
 *
 * Результат:
 * - 1.0 = векторы идентичны (угол 0°)
 * - 0.0 = векторы ортогональны (угол 90°)
 * - -1.0 = векторы противоположны (угол 180°)
 *
 * Для нормализованных векторов (норма = 1.0), косинусное сходство = скалярное произведение.
 * OpenAI embeddings уже нормализованы, поэтому можем использовать dot product напрямую.
 */
public class CosineSimilarity {

    /**
     * Вычисляет косинусное сходство между двумя векторами.
     *
     * Оптимизированная версия для нормализованных векторов:
     * просто вычисляет скалярное произведение (dot product).
     *
     * @param vectorA первый вектор
     * @param vectorB второй вектор
     * @return косинусное сходство в диапазоне [-1.0, 1.0]
     * @throws IllegalArgumentException если векторы разной длины
     */
    public static float compute(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException(
                String.format("Векторы должны быть одинаковой длины. A=%d, B=%d",
                    vectorA.length, vectorB.length)
            );
        }

        // Для нормализованных векторов: cosine similarity = dot product
        return dotProduct(vectorA, vectorB);
    }

    /**
     * Вычисляет косинусное сходство с полной нормализацией.
     *
     * Используйте этот метод если векторы НЕ нормализованы.
     * Для OpenAI embeddings предпочитайте compute() - она быстрее.
     *
     * @param vectorA первый вектор
     * @param vectorB второй вектор
     * @return косинусное сходство в диапазоне [-1.0, 1.0]
     * @throws IllegalArgumentException если векторы разной длины или нулевые
     */
    public static float computeWithNormalization(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException(
                String.format("Векторы должны быть одинаковой длины. A=%d, B=%d",
                    vectorA.length, vectorB.length)
            );
        }

        float dotProd = dotProduct(vectorA, vectorB);
        float normA = norm(vectorA);
        float normB = norm(vectorB);

        if (normA == 0.0f || normB == 0.0f) {
            throw new IllegalArgumentException("Нулевой вектор не имеет косинусного сходства");
        }

        return dotProd / (normA * normB);
    }

    /**
     * Вычисляет скалярное произведение (dot product) двух векторов.
     *
     * dot(A, B) = A[0]*B[0] + A[1]*B[1] + ... + A[n]*B[n]
     *
     * @param vectorA первый вектор
     * @param vectorB второй вектор
     * @return скалярное произведение
     */
    public static float dotProduct(float[] vectorA, float[] vectorB) {
        float sum = 0.0f;
        for (int i = 0; i < vectorA.length; i++) {
            sum += vectorA[i] * vectorB[i];
        }
        return sum;
    }

    /**
     * Вычисляет норму (длину) вектора.
     *
     * ||A|| = sqrt(A[0]² + A[1]² + ... + A[n]²)
     *
     * @param vector вектор
     * @return норма вектора
     */
    public static float norm(float[] vector) {
        float sumOfSquares = 0.0f;
        for (float value : vector) {
            sumOfSquares += value * value;
        }
        return (float) Math.sqrt(sumOfSquares);
    }

    /**
     * Проверяет, нормализован ли вектор (норма ≈ 1.0).
     *
     * @param vector вектор для проверки
     * @param epsilon допустимая погрешность (обычно 0.001)
     * @return true если вектор нормализован
     */
    public static boolean isNormalized(float[] vector, float epsilon) {
        float vectorNorm = norm(vector);
        return Math.abs(vectorNorm - 1.0f) < epsilon;
    }

    /**
     * Нормализует вектор (изменяет исходный массив).
     *
     * После нормализации: ||vector|| = 1.0
     *
     * @param vector вектор для нормализации
     * @throws IllegalArgumentException если вектор нулевой
     */
    public static void normalize(float[] vector) {
        float vectorNorm = norm(vector);

        if (vectorNorm == 0.0f) {
            throw new IllegalArgumentException("Нельзя нормализовать нулевой вектор");
        }

        for (int i = 0; i < vector.length; i++) {
            vector[i] /= vectorNorm;
        }
    }

    /**
     * Создает нормализованную копию вектора (не изменяет оригинал).
     *
     * @param vector исходный вектор
     * @return нормализованная копия
     * @throws IllegalArgumentException если вектор нулевой
     */
    public static float[] normalized(float[] vector) {
        float[] copy = vector.clone();
        normalize(copy);
        return copy;
    }
}
