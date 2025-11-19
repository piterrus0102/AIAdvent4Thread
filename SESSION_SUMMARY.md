# Суммаризация сессии разработки

**Дата:** 19 ноября 2025  
**Проект:** AIAdvent4Thread - День 12 (Планировщик + MCP)

---

## 🎯 Главные достижения

### 1. **Tool Chaining (Цепочка вызовов инструментов)** 🔗

**Проблема:**  
LLM вызывала инструмент, получала результат, но не могла вызвать следующий инструмент для продолжения работы. Например:
- Запрос: "Сколько комментариев к PR в репозитории AIAdvent4Thread?"
- LLM вызывала `search_repositories` ✅
- Получала результат, но ответ `USE_TOOL: list_pull_requests` отправлялся как текст пользователю ❌

**Решение:**
- Реализован **цикл вызовов инструментов** в `MainServer.callLLM()`
- Добавлен лимит `MAX_TOOL_CALLS = 5` для предотвращения бесконечных циклов
- Результаты каждого инструмента передаются обратно в LLM для следующего решения
- LLM может вызывать несколько инструментов последовательно до получения финального ответа

**Код:**
```javascript
while (toolCallCount < MAX_TOOL_CALLS) {
    // 1. Отправляем запрос в YandexGPT
    // 2. Проверяем, есть ли в ответе USE_TOOL
    // 3. Если да - вызываем инструмент
    // 4. Результат добавляем в историю
    // 5. Повторяем, пока LLM не даст финальный ответ
}
```

**Результат:**
```
✅ Цепочка: search_repositories → list_pull_requests → pull_request_read → финальный ответ
```

---

### 2. **Graceful Error Handling (Обработка ошибок инструментов)** 🛡️

**Проблема:**
При ошибке вызова инструмента (например, отсутствующий параметр или несуществующий инструмент) весь запрос крашился, и пользователь получал серверную ошибку.

**Пример ошибок:**
```
- issue_read: "missing required parameter: method"
- pull_request_read: "missing required parameter: method"
- list_issue_comments: "tool not found" (инструмент не существует)
```

**Решение:**
- Добавлен `try-catch` вокруг вызова инструментов
- При ошибке:
  - Не крашим запрос ✅
  - Передаем текст ошибки обратно в LLM как результат инструмента
  - LLM может попробовать другой инструмент или сообщить об ограничениях

**Код:**
```javascript
try {
    const toolResult = await activeMCPClient.callTool(toolName, toolArgs);
    toolResultText = toolResult.content[0].text;
} catch (toolError) {
    toolResultText = `Ошибка выполнения инструмента ${toolName}: ${toolError.message}`;
    console.error('[Server] ❌ Ошибка выполнения инструмента:', toolError);
}
```

**Результат:**
```
❌ Раньше: Ошибка → КРАШ → Пользователь видит "Internal Server Error"
✅ Теперь: Ошибка → LLM получает фидбек → Пробует другой подход → Финальный ответ
```

---

### 3. **Валидация инструментов** ✅

**Проблема:**
LLM "галлюцинировала" - придумывала несуществующие инструменты на основе логики и похожих названий:
- Видит `list_issues`, `list_branches`, `list_commits`
- Думает: "Должен быть и `list_issue_comments`!"
- Пытается вызвать → краш ❌

**Решение:**

#### 3.1. Улучшен System Prompt
```
КРИТИЧНО ВАЖНО:
- Используй ТОЛЬКО инструменты из списка выше
- НЕ придумывай новые инструменты
- НЕ используй инструменты с похожими названиями, если их нет в списке
- Проверь, что инструмент есть в списке перед вызовом
```

#### 3.2. Валидация на сервере
Перед вызовом проверяем, что инструмент существует:
```javascript
const availableToolNames = tools.map(t => t.name);
if (!availableToolNames.includes(toolName)) {
    // Передаем ошибку LLM со списком доступных инструментов
    // Даем LLM шанс попробовать правильный инструмент
    continue;
}
```

**Результат:**
```
❌ Раньше: LLM вызывает list_issue_comments → краш
✅ Теперь: LLM вызывает list_issue_comments → ошибка → 
           получает список доступных инструментов → 
           пробует правильный инструмент
```

---

### 4. **Копирование сообщений (Long Tap)** 📋

**Фича:**
Долгое нажатие на любое сообщение в чате копирует его текст в буфер обмена.

**Реализация:**

#### 4.1. UI (ServerChatScreen.kt)
```kotlin
Surface(
    modifier = Modifier
        .widthIn(max = 280.dp)
        .combinedClickable(
            onClick = { },
            onLongClick = { onLongClick(message.text) }
        )
)
```

#### 4.2. Clipboard Manager
```kotlin
val clipboardManager = LocalClipboardManager.current
clipboardManager.setText(AnnotatedString(text))
```

#### 4.3. Snackbar уведомление
```kotlin
LaunchedEffect(state.snackbarMessage) {
    state.snackbarMessage?.let { message ->
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short
        )
    }
}
```

**Результат:**
```
👆 Долгое нажатие на сообщение → 📋 Копируется в буфер → 💬 "Текст скопирован"
```

---

## 📁 Измененные файлы

### Backend (Node.js)

1. **`mcp-proxy/localserver.js`**
   - Реализован цикл tool chaining в `callLLM()`
   - Добавлена валидация инструментов перед вызовом
   - Улучшен system prompt для предотвращения галлюцинаций
   - Добавлена обработка ошибок инструментов (`try-catch`)
   - Добавлено логирование цепочки вызовов
   - Трекинг использованных инструментов (`usedTools`)

### Android App (Kotlin)

2. **`ServerChatScreenIntent.kt`**
   - Добавлен `CopyMessageToClipboard(text: String)` intent

3. **`ServerChatScreenState.kt`**
   - Добавлено поле `snackbarMessage: String?`

4. **`ServerChatScreenViewModel.kt`**
   - Добавлен метод `showSnackbar(message: String)`
   - Обработка `CopyMessageToClipboard` intent
   - Автоматическое скрытие Snackbar через 2 секунды

5. **`ServerChatScreen.kt`**
   - Добавлен импорт `combinedClickable`, `LocalClipboardManager`, `AnnotatedString`
   - Добавлен `SnackbarHost` в `Scaffold`
   - Реализован долгое нажатие на `MessageBubble`
   - Интеграция с ClipboardManager
   - LaunchedEffect для показа Snackbar

---

## 🔧 Технические детали

### Tool Chaining Flow

```
1. Пользователь: "Сколько комментариев к PR?"
   ↓
2. LLM → USE_TOOL: search_repositories
   ↓
3. Server → GitHub MCP → Результат
   ↓
4. LLM получает результат → USE_TOOL: list_pull_requests
   ↓
5. Server → GitHub MCP → Результат
   ↓
6. LLM получает результат → USE_TOOL: pull_request_read
   ↓
7. Server → Ошибка: "missing required parameter: method"
   ↓
8. LLM получает ошибку → USE_TOOL: другой подход
   ↓
9. LLM → Финальный ответ пользователю
```

### Error Handling Flow

```
Tool Call
   ↓
Try {
   Execute Tool
   Success → Return Result
}
   ↓
Catch {
   Error → Return Error Message to LLM
}
   ↓
LLM receives error → Try alternative approach
   ↓
Final response to user
```

---

## 📊 Метрики улучшений

| Метрика | До | После |
|---------|-----|-------|
| **Последовательные вызовы инструментов** | ❌ Невозможно | ✅ До 5 вызовов |
| **Обработка ошибок инструментов** | ❌ Краш | ✅ Graceful recovery |
| **Галлюцинации инструментов** | ❌ Краш | ✅ Валидация + feedback |
| **Копирование сообщений** | ❌ Нет | ✅ Long tap |
| **Пользовательский опыт** | 😞 Частые ошибки | 😊 Надежная работа |

---

## 🚀 Следующие шаги (опционально)

### Возможные улучшения:

1. **Увеличить MAX_TOOL_CALLS** до 10 для сложных запросов
2. **Добавить кэширование** результатов инструментов
3. **Логирование цепочек** в БД для аналитики
4. **Визуализация цепочки** в UI (показать, какие инструменты были вызваны)
5. **Retry механизм** для failed tool calls
6. **Копирование с форматированием** (Markdown → styled text)
7. **Контекстное меню** на долгом нажатии (Копировать / Переслать / Удалить)

---

## 🐛 Устраненные баги

1. ✅ LLM не могла вызывать несколько инструментов подряд
2. ✅ Ошибки инструментов крашили весь запрос
3. ✅ LLM галлюцинировала несуществующие инструменты
4. ✅ Отсутствовала обратная связь при ошибках
5. ✅ Невозможно скопировать текст сообщения

---

## 📝 Заметки

- **YandexGPT** хорошо справляется с tool chaining, когда в промпте четко указаны доступные инструменты
- **GitHub MCP Server** (40 инструментов) требует тщательной валидации, так как не все инструменты имеют единообразный формат параметров
- **Error feedback loop** позволяет LLM адаптироваться к ограничениям API и находить альтернативные пути решения задачи
- **Long tap** - стандартный UX паттерн для мобильных приложений, интуитивно понятный пользователям

---

## 👨‍💻 Автор

Сессия разработки с AI Assistant (Claude Sonnet 4.5)  
Проект: AIAdvent4Thread  
День 12: Планировщик + MCP Integration

---

**Статус:** ✅ Все задачи выполнены и протестированы

