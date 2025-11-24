# Данные для векторизации и семантического поиска

## Созданные файлы

### 1. `course_chunks_for_vectorization.json` ⭐ **РЕКОМЕНДУЕТСЯ**
**Использовать этот файл для векторизации!**

- **Формат**: Массив JSON объектов
- **Размер**: 4 чанка (по одному на урок)
- **Объем**: ~23 KB текста

**Структура чанка:**
```json
{
  "id": "lesson_1",
  "source": "android-studio-course",
  "metadata": {
    "course": "Android Studio для непрограммистов",
    "course_slug": "android-studio",
    "section": "Введение",
    "section_order": 1,
    "lesson_title": "Что такое Android Studio...",
    "lesson_slug": "intro-android-studio",
    "lesson_order": 1,
    "url": "/android-studio/lesson/intro-android-studio"
  },
  "content": "# Заголовок урока\n\nКурс: ...\nРаздел: ...\n\n[Полный контент урока]"
}
```

**Преимущества:**
- ✅ Каждый урок = отдельный чанк (оптимально для поиска)
- ✅ Метаданные для фильтрации и ссылок
- ✅ Готов для embedding моделей (OpenAI, Cohere, etc.)
- ✅ Легко интегрировать в векторные БД (Pinecone, Weaviate, Qdrant)

---

### 2. `course_data_for_vectorization.json`
**Полная структура курса в виде дерева**

- **Формат**: Один JSON объект с вложенной структурой
- **Использование**: Для анализа структуры курса или создания собственной логики чанкинга

**Структура:**
```json
{
  "id": 100,
  "title": "Android Studio для непрограммистов",
  "sections": [
    {
      "id": 1,
      "title": "Введение",
      "lessons": [
        {
          "id": 1,
          "title": "...",
          "content": "..."
        }
      ]
    }
  ]
}
```

---

## Как использовать для векторизации

### Вариант 1: OpenAI Embeddings + Pinecone/Qdrant

```python
import json
import openai
from pinecone import Pinecone

# Загружаем чанки
with open('course_chunks_for_vectorization.json', 'r') as f:
    chunks = json.load(f)

# Векторизуем каждый чанк
for chunk in chunks:
    # Создаем embedding
    embedding = openai.Embedding.create(
        input=chunk['content'],
        model="text-embedding-3-small"
    )
    
    # Сохраняем в векторную БД
    index.upsert([{
        'id': chunk['id'],
        'values': embedding['data'][0]['embedding'],
        'metadata': chunk['metadata']
    }])
```

### Вариант 2: LangChain

```python
from langchain.embeddings import OpenAIEmbeddings
from langchain.vectorstores import Chroma
from langchain.docstore.document import Document
import json

# Загружаем чанки
with open('course_chunks_for_vectorization.json', 'r') as f:
    chunks = json.load(f)

# Преобразуем в LangChain документы
documents = [
    Document(
        page_content=chunk['content'],
        metadata=chunk['metadata']
    )
    for chunk in chunks
]

# Создаем векторное хранилище
embeddings = OpenAIEmbeddings()
vectorstore = Chroma.from_documents(
    documents=documents,
    embedding=embeddings,
    persist_directory="./chroma_db"
)
```

### Вариант 3: Llamaindex

```python
from llama_index import Document, VectorStoreIndex
import json

# Загружаем чанки
with open('course_chunks_for_vectorization.json', 'r') as f:
    chunks = json.load(f)

# Создаем документы
documents = [
    Document(
        text=chunk['content'],
        metadata=chunk['metadata'],
        doc_id=chunk['id']
    )
    for chunk in chunks
]

# Создаем индекс
index = VectorStoreIndex.from_documents(documents)
```

---

## Примеры поисковых запросов

После векторизации можно делать семантический поиск:

```python
# Поиск по вопросу
query = "Как работает Power Save Mode в Android Studio?"
results = vectorstore.similarity_search(query, k=3)

# Фильтрация по секции
results = vectorstore.similarity_search(
    query,
    filter={"section": "Интерфейс и ориентация в IDE"}
)

# Получение контекста для RAG
context = "\n\n".join([doc.page_content for doc in results])
```

---

## Обновление данных

Для обновления данных после изменения БД:

```bash
cd /Users/ruslanhafizov/Desktop/zayobushek

# Экспортируем обновленные данные
python3 << 'PYEOF'
import sqlite3
import json

conn = sqlite3.connect('database.sqlite')
conn.row_factory = sqlite3.Row
cursor = conn.cursor()

cursor.execute("""
    SELECT 
        c.title as course_title,
        c.slug as course_slug,
        s.title as section_title,
        s.order_index as section_order,
        l.id as lesson_id,
        l.title as lesson_title,
        l.slug as lesson_slug,
        l.description as lesson_description,
        l.content as lesson_content,
        l.order_index as lesson_order
    FROM lessons l
    JOIN sections s ON l.section_id = s.id
    JOIN courses c ON l.course_id = c.id
    WHERE c.slug = 'android-studio'
    ORDER BY s.order_index, l.order_index
""")

lessons = cursor.fetchall()
chunks = []

for lesson in lessons:
    chunk = {
        "id": f"lesson_{lesson['lesson_id']}",
        "source": "android-studio-course",
        "metadata": {
            "course": lesson['course_title'],
            "course_slug": lesson['course_slug'],
            "section": lesson['section_title'],
            "section_order": lesson['section_order'],
            "lesson_title": lesson['lesson_title'],
            "lesson_slug": lesson['lesson_slug'],
            "lesson_order": lesson['lesson_order'],
            "url": f"/android-studio/lesson/{lesson['lesson_slug']}"
        },
        "content": f"""# {lesson['lesson_title']}

Курс: {lesson['course_title']}
Раздел: {lesson['section_title']}

{lesson['lesson_description']}

{lesson['lesson_content'] or ''}"""
    }
    chunks.append(chunk)

with open('course_chunks_for_vectorization.json', 'w', encoding='utf-8') as f:
    json.dump(chunks, f, ensure_ascii=False, indent=2)

print(f"✅ Обновлено: {len(chunks)} чанков")
conn.close()
PYEOF
```

---

## Статистика

- **Курс**: Android Studio для непрограммистов
- **Секций**: 2
- **Уроков**: 4
- **Объем контента**: 23,606 символов
- **Формат**: JSON (UTF-8)

---

## Рекомендации

1. **Для RAG системы** - используйте `course_chunks_for_vectorization.json`
2. **Размер чанка** - каждый урок целиком (оптимально для контекста)
3. **Embedding модель** - рекомендуется `text-embedding-3-small` (1536 dimensions)
4. **Метаданные** - используйте для фильтрации и построения ссылок в ответах

---

## Интеграция с вашим проектом

После векторизации вы можете:

1. **Семантический поиск** - найти релевантные уроки по запросу
2. **RAG (Retrieval-Augmented Generation)** - использовать контент для генерации ответов
3. **Рекомендации** - предлагать похожие уроки
4. **Чат-бот** - отвечать на вопросы по содержанию курса

Пример RAG pipeline:
```
Вопрос пользователя 
  → Векторный поиск по чанкам
  → Получение топ-3 релевантных уроков
  → Передача контекста в LLM
  → Генерация ответа с ссылками на уроки
```

