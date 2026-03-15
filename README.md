# NotebookRAG

**Образовательный RAG (Retrieval-Augmented Generation) пайплайн на Java 17+**

Полнофункциональное терминальное приложение для изучения концепций RAG, включающее загрузку документов, chunking, векторные эмбеддинги, семантический поиск и генерацию ответов через LLM.

## Что такое RAG?

RAG (Retrieval-Augmented Generation) - это техника, которая комбинирует:
1. **Retrieval** (извлечение): поиск релевантной информации из базы знаний
2. **Augmentation** (дополнение): добавление найденной информации в контекст
3. **Generation** (генерация): создание ответа через LLM на основе дополненного контекста

Это позволяет LLM отвечать на вопросы на основе ваших документов, а не только на основе данных обучения.

## Особенности проекта

- **Образовательный фокус**: каждый компонент RAG изолирован и прозрачен
- **Терминальный интерфейс**: чистое CLI приложение без GUI
- **Полная наблюдаемость**: видимость каждого этапа пайплайна
- **Модульная архитектура**: 6 независимых компонентов RAG
- **Поддержка форматов**: TXT, PDF, DOCX
- **Детальное логирование**: все операции логируются на русском языке

## Быстрый старт

### Предварительные требования

- **JDK 17+** ([скачать](https://adoptium.net/))
- **Maven 3.9+** ([скачать](https://maven.apache.org/download.cgi))
- **OpenAI API key** ([получить](https://platform.openai.com/api-keys))

### Установка

```bash
# 1. Клонировать репозиторий
git clone <repository-url>
cd NoteBookRAG

# 2. Установить API ключ
export OPENAI_API_KEY="sk-your-api-key-here"

# 3. Собрать проект
mvn clean package

# 4. Создать alias (опционально)
alias notebook-rag='java -jar target/notebookrag-1.0.0.jar'
```

### Первые шаги

```bash
# 1. Загрузить документ
notebook-rag ingest mydocument.pdf

# 2. Задать вопрос
notebook-rag query "Что говорится в документе о Java?"

# 3. Посмотреть чанки
notebook-rag list-chunks mydocument.pdf

# 4. Детальный режим
notebook-rag query "Что такое интерфейс?" --verbose --show-sources
```

## Архитектура RAG пайплайна

```
┌──────────────┐
│  Document    │  (TXT/PDF/DOCX)
└──────┬───────┘
       │
       ↓ 1. Parsing
┌──────────────┐
│  Text        │
└──────┬───────┘
       │
       ↓ 2. Chunking (фиксированный размер + overlap)
┌──────────────┐
│  Chunks[]    │  ["chunk1", "chunk2", ...]
└──────┬───────┘
       │
       ↓ 3. Embedding (OpenAI API)
┌──────────────┐
│  Vectors[]   │  [float[1536], float[1536], ...]
└──────┬───────┘
       │
       ↓ 4. Storage (.ser files)
┌──────────────┐
│  Disk        │
└──────────────┘

User Query → Embed → Similarity Search (cosine) → Top-K Chunks → LLM → Answer
```

## Основные команды

### Загрузка документов (ingest)

```bash
# Базовое использование
notebook-rag ingest document.pdf

# С настройками chunking
notebook-rag ingest document.pdf --chunk-size 600 --overlap 100

# Множественные документы
notebook-rag ingest doc1.txt doc2.pdf doc3.docx
```

**Параметры:**
- `--chunk-size` - размер чанка в символах (по умолчанию: 500)
- `--overlap` - размер перекрытия в символах (по умолчанию: 50)

### Запрос (query)

```bash
# Простой запрос
notebook-rag query "Что такое полиморфизм?"

# С детальным выводом
notebook-rag query "Объясни наследование" --verbose

# С источниками
notebook-rag query "Что такое интерфейс?" --show-sources

# Настройка retrieval
notebook-rag query "Вопрос" --top-k 5 --model gpt-4o
```

**Параметры:**
- `--top-k` - количество извлекаемых чанков (по умолчанию: 3)
- `--model` - LLM модель (по умолчанию: gpt-4o-mini)
- `--verbose` - детальный вывод всех этапов RAG
- `--show-sources` - показать использованные чанки

### Инспектирование (inspect)

```bash
# Список всех чанков документа
notebook-rag list-chunks document.pdf

# Детальный просмотр конкретного чанка
notebook-rag inspect-chunks document.pdf --chunk-id 5

# Статистика по чанкам
notebook-rag chunk-stats document.pdf

# Визуализация границ чанков
notebook-rag show-boundaries document.pdf --limit 10
```

### Статистика (stats)

```bash
# Список всех загруженных документов
notebook-rag list-documents

# Информация о конкретном документе
notebook-rag document-info document.pdf
```

## Компоненты RAG

### 1. Document Ingestion (Загрузка документов)

**Файлы:** `src/main/java/com/notebookrag/ingestion/`

- `DocumentIngestionService.java` - оркестрация загрузки
- `TextDocumentParser.java` - парсинг TXT
- `PdfDocumentParser.java` - парсинг PDF (Apache PDFBox)
- `DocxDocumentParser.java` - парсинг DOCX (Apache POI)

**Концепция:** Извлечение текста из различных форматов документов

### 2. Chunking (Разбиение на чанки)

**Файлы:** `src/main/java/com/notebookrag/chunking/`

- `ChunkingService.java` - сервис разбиения
- `FixedSizeChunker.java` - фиксированное разбиение с перекрытием
- `ChunkingStrategy.java` - интерфейс стратегии

**Концепция:** Разбиение текста на фрагменты фиксированного размера с перекрытием для сохранения контекста на границах

**Параметры:**
- Размер чанка: ~500 символов (настраивается)
- Перекрытие: ~10% от размера чанка

### 3. Embedding (Векторизация)

**Файлы:** `src/main/java/com/notebookrag/embedding/`

- `EmbeddingService.java` - генерация эмбеддингов
- `OpenAIEmbeddingClient.java` - HTTP клиент для OpenAI API

**Концепция:** Преобразование текста в векторное представление (float[1536]) для семантического поиска

**Модель:** `text-embedding-3-small` (1536 измерений)

### 4. Vector Storage (Хранение векторов)

**Файлы:** `src/main/java/com/notebookrag/storage/`

- `VectorStorageService.java` - интерфейс хранилища
- `SerializationStorage.java` - файловое хранилище (.ser файлы)

**Концепция:** Сохранение чанков и их эмбеддингов в файловую систему

**Структура:**
```
data/vectors/
  └── {document-id}/
      ├── metadata.ser    # Document
      ├── chunks.ser      # List<Chunk>
      └── embeddings.ser  # List<Embedding>
```

### 5. Retrieval (Поиск)

**Файлы:** `src/main/java/com/notebookrag/retrieval/`

- `RetrievalService.java` - поиск релевантных чанков
- `CosineSimilarity.java` - вычисление косинусного сходства

**Концепция:** Поиск top-K наиболее релевантных чанков через косинусное сходство векторов

**Формула:**
```
similarity(A, B) = (A · B) / (||A|| * ||B||)
```

**Результат:** значение от 0.0 (несхожи) до 1.0 (идентичны)

### 6. Generation (Генерация ответов)

**Файлы:** `src/main/java/com/notebookrag/generation/`

- `GenerationService.java` - генерация ответов
- `OpenAILLMClient.java` - HTTP клиент для OpenAI Chat API

**Концепция:** Генерация ответа через LLM на основе найденных чанков

**Модель:** `gpt-4o-mini` (баланс качество/цена)

**Промпт структура:**
```
System: Ты помощник, отвечающий на вопросы на основе предоставленного контекста.

User: Контекст:
[Chunk 1 текст]
[Chunk 2 текст]
[Chunk 3 текст]

Вопрос: {user query}
```

## Конфигурация

### Конфигурационный файл

Создайте `~/.notebookrag/config.properties`:

```properties
# Chunking параметры
chunking.default.size=500
chunking.default.overlap=50

# Retrieval параметры
retrieval.default.topk=3

# OpenAI модели
openai.embedding.model=text-embedding-3-small
openai.llm.model=gpt-4o-mini

# Хранилище
storage.path=./data/vectors

# Логирование
logging.level=INFO
logging.file=logs/notebookrag.log
```

### Environment переменные

```bash
export OPENAI_API_KEY="sk-..."
export NOTEBOOKRAG_STORAGE_PATH="/custom/path"
export NOTEBOOKRAG_LOG_LEVEL="DEBUG"
```

### Приоритет конфигурации

1. **CLI флаги** (высший приоритет)
2. **Environment переменные**
3. **Конфигурационный файл**
4. **Defaults в коде** (низший приоритет)

## Примеры использования

### Пример 1: Обработка учебника по Java

```bash
# 1. Загрузка документа
notebook-rag ingest java-tutorial.pdf

# Вывод:
# Загрузка документа: java-tutorial.pdf
# Извлечение текста: 100% [====================] (42 страницы)
# Разбиение на чанки: 127 чанков создано
# Генерация эмбеддингов: 100% [====================] (127/127)
# Сохранение: data/vectors/8f3a9c12/
# Документ успешно обработан

# 2. Просмотр чанков
notebook-rag chunk-stats java-tutorial.pdf

# Вывод:
# Статистика чанков для java-tutorial.pdf:
# Всего чанков: 127
# Средний размер: 487 символов
# Размер overlap: 50 символов
# Стратегия: FIXED_SIZE

# 3. Запрос
notebook-rag query "Что такое интерфейс в Java?"

# Вывод:
# Интерфейс в Java - это контракт, который определяет набор методов,
# которые класс должен реализовать. Интерфейс не содержит реализации
# методов (до Java 8), только их сигнатуры...

# 4. Запрос с источниками
notebook-rag query "Объясни полиморфизм" --show-sources

# Вывод:
# Полиморфизм в Java позволяет объектам разных классов обрабатываться
# через общий интерфейс...
#
# [Источники]
# Chunk #42 (similarity: 0.89): "Полиморфизм - это способность объекта..."
# Chunk #87 (similarity: 0.85): "В Java полиморфизм реализуется через..."
# Chunk #103 (similarity: 0.82): "Пример полиморфизма: Animal animal = new Dog()..."
```

### Пример 2: Эксперименты с chunking

```bash
# Сравнение различных размеров чанков
notebook-rag ingest document.txt --chunk-size 300 --overlap 30
notebook-rag chunk-stats document.txt

notebook-rag ingest document.txt --chunk-size 700 --overlap 70
notebook-rag chunk-stats document.txt

# Визуализация границ
notebook-rag show-boundaries document.txt --limit 5

# Вывод показывает как изменилось разбиение
```

### Пример 3: Verbose режим для обучения

```bash
notebook-rag query "Что такое наследование?" --verbose

# Вывод:
# === EMBEDDING QUERY ===
# Модель: text-embedding-3-small
# Размерность: 1536
# Время: 156ms
#
# === RETRIEVAL ===
# Top-K: 3
# Найдено чанков: 3
# Chunk #23 - similarity: 0.91
# Chunk #45 - similarity: 0.87
# Chunk #12 - similarity: 0.84
#
# === GENERATION ===
# Модель: gpt-4o-mini
# Промпт: 892 токена
# Ответ: 143 токена
# Всего токенов: 1035
# Время: 2.3 секунды
#
# === ОТВЕТ ===
# Наследование в Java - это механизм...
```

## Производительность

### Ожидаемые показатели

| Операция | Время | Примечания |
|----------|-------|-----------|
| Парсинг PDF (10 страниц) | < 2 сек | Apache PDFBox |
| Парсинг DOCX (10 страниц) | < 1 сек | Apache POI |
| Chunking (5KB текста) | < 100ms | Чистая Java логика |
| Генерация эмбеддингов (100 чанков) | 5-10 сек | OpenAI API latency |
| Сериализация | < 500ms | Java serialization |
| Поиск (1000 чанков) | < 50ms | In-memory вычисления |
| LLM генерация | 2-5 сек | Зависит от длины ответа |

### Ограничения

- Максимальный размер документа: **50MB** (настраивается)
- Максимальное количество чанков: **10,000**
- OpenAI rate limits: 3000 RPM (embeddings), 500 RPM (chat)

### Оптимизация памяти

Для больших документов:
```bash
java -Xmx4g -jar target/notebookrag-1.0.0.jar ingest large-document.pdf
```

## Troubleshooting

### OpenAI API ключ не найден

**Проблема:**
```
ОШИБКА: OpenAI API key не найден
```

**Решение:**
```bash
export OPENAI_API_KEY="sk-your-key-here"

# Для постоянного использования добавьте в ~/.bashrc или ~/.zshrc:
echo 'export OPENAI_API_KEY="sk-your-key-here"' >> ~/.bashrc
source ~/.bashrc
```

### Rate limit exceeded

**Проблема:**
```
ОШИБКА: OpenAI API вернул ошибку 429 (rate limit exceeded)
```

**Решение:**
- Приложение автоматически выполняет retry с exponential backoff
- Подождите несколько минут между запросами
- Уменьшите chunk size для меньшего количества API вызовов

### Out of memory

**Проблема:**
```
java.lang.OutOfMemoryError: Java heap space
```

**Решение:**
```bash
# Увеличьте heap size
java -Xmx4g -jar target/notebookrag-1.0.0.jar ingest large-doc.pdf
```

### Corrupted PDF

**Проблема:**
```
ОШИБКА: Не удалось извлечь текст из PDF
```

**Решение:**
- Проверьте PDF в другом ридере
- Пересохраните PDF через Adobe Reader / Preview
- Конвертируйте в TXT или DOCX формат

## Технологии

### Зависимости

- **picocli 4.7+** - CLI framework
- **Jackson 2.16+** - JSON обработка
- **Apache PDFBox 3.0+** - парсинг PDF
- **Apache POI 5.2+** - парсинг DOCX
- **SLF4J 2.0+ + Logback 1.4+** - логирование
- **Java 11+ HttpClient** - HTTP клиент для OpenAI API

### Структура проекта

```
src/
└── main/
    ├── java/com/notebookrag/
    │   ├── Main.java                      # Точка входа
    │   ├── cli/                           # CLI команды
    │   │   ├── NotebookRagCLI.java
    │   │   ├── IngestCommand.java
    │   │   ├── QueryCommand.java
    │   │   ├── InspectCommand.java
    │   │   └── StatsCommand.java
    │   ├── domain/                        # Модель данных
    │   │   ├── Document.java
    │   │   ├── Chunk.java
    │   │   ├── Embedding.java
    │   │   ├── Query.java
    │   │   ├── RetrievalResult.java
    │   │   └── GeneratedResponse.java
    │   ├── ingestion/                     # Загрузка документов
    │   ├── chunking/                      # Разбиение на чанки
    │   ├── embedding/                     # Генерация эмбеддингов
    │   ├── storage/                       # Хранение векторов
    │   ├── retrieval/                     # Поиск
    │   ├── generation/                    # Генерация ответов
    │   ├── config/                        # Конфигурация
    │   └── utils/                         # Утилиты
    └── resources/
        ├── application.properties
        └── logback.xml
```

## Обучение и эксперименты

### Понимание Chunking

```bash
# Сравните разные размеры чанков
notebook-rag ingest doc.txt --chunk-size 200 --overlap 20
notebook-rag show-boundaries doc.txt

notebook-rag ingest doc.txt --chunk-size 800 --overlap 80
notebook-rag show-boundaries doc.txt

# Наблюдайте как границы влияют на retrieval
```

### Понимание Embeddings

```bash
# Посмотрите на эмбеддинги
notebook-rag inspect-chunks doc.txt --chunk-id 1

# Вы увидите:
# Первые 5 компонентов вектора: [0.0123, -0.0456, 0.0789, ...]
# Каждое число - семантическое представление текста
```

### Понимание Retrieval

```bash
# Используйте verbose mode
notebook-rag query "вопрос" --verbose

# Обратите внимание на similarity scores:
# 0.9-1.0 = очень релевантно
# 0.7-0.9 = релевантно
# < 0.7 = слабая релевантность
```

## Дальнейшее развитие

Этот проект - **Phase 1** (базовый RAG). Возможные улучшения:

### Phase 2: Продвинутый RAG
- Semantic chunking (разбиение по предложениям)
- Re-ranking моделей для улучшения retrieval
- Metadata фильтры (дата, автор, тип)
- Hybrid search (keyword + semantic)

### Phase 3: Production features
- Векторная БД (Pinecone, Weaviate, ChromaDB)
- Multi-query retrieval
- Память диалога (conversation history)
- Web интерфейс

## Ресурсы

- [OpenAI Embeddings API](https://platform.openai.com/docs/guides/embeddings)
- [OpenAI Chat API](https://platform.openai.com/docs/guides/text-generation)
- [RAG Concepts](https://www.pinecone.io/learn/retrieval-augmented-generation/)
- [Apache PDFBox](https://pdfbox.apache.org/)
- [Apache POI](https://poi.apache.org/)
- [picocli](https://picocli.info/)

## Лицензия

MIT License

## Контрибьюции

Проект создан в образовательных целях. Pull requests приветствуются!

---

**Версия:** 1.0.0
**Дата:** 2026-03-08
**Язык:** Java 17+
