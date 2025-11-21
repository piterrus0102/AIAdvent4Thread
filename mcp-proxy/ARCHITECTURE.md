# 🏗️ Архитектура проекта (для Android разработчика)

## 📂 Структура файлов

```
mcp-proxy/
├── index.js                          ← 🚪 Entry Point (MainActivity)
│
├── server/
│   └── MainServer.js                 ← 🧠 Business Logic (ViewModel + UseCases)
│
├── mcp/
│   ├── MCPServer.js                  ← 🔧 Local Data Provider (LocalDataSource)
│   ├── MCPClient.js                  ← 📦 Local Repository (Repository)
│   └── GitHubMCPClient.js            ← 🐙 GitHub API Client (RemoteDataSource)
│
├── database/
│   └── AppDatabase.js                ← 💾 SQLite Database (Room)
│
├── count.json                        ← 📄 Local Storage (SharedPreferences)
├── app_data.db                       ← 💾 Database File
└── github-mcp-server                 ← 🦾 Go Binary (External Service)
```

---

## 🎯 Связи между компонентами (Dependency Graph)

### Аналог Android Clean Architecture:

```
┌─────────────────────────────────────────────────────────────────┐
│                      Android App (Client)                        │
│                     Presentation Layer                           │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP REST API
                            ↓
┌───────────────────────────────────────────────────────────────────┐
│                         index.js                                  │
│                   Express Server (API Layer)                      │
│                   Аналог: @RestController / Ktor Routes          │
└───────────────────────────┬───────────────────────────────────────┘
                            │
                            ↓
┌───────────────────────────────────────────────────────────────────┐
│                      MainServer.js                                │
│             Orchestrator (ViewModel + UseCases)                   │
│                                                                   │
│  - Управляет бизнес-логикой                                      │
│  - Вызывает LLM (YandexGPT)                                      │
│  - Переключает между локальным и GitHub MCP                      │
│  - Координирует все компоненты                                   │
└─────┬──────────────┬──────────────┬──────────────┬───────────────┘
      │              │              │              │
      ↓              ↓              ↓              ↓
┌──────────┐   ┌─────────────┐  ┌────────────┐  ┌──────────────┐
│ MCPClient│   │ GitHubMCP   │  │ AppDatabase│  │  YandexGPT   │
│          │   │   Client    │  │            │  │  (External)  │
│ Аналог:  │   │ Аналог:     │  │ Аналог:    │  │  API         │
│ Local    │   │ Remote      │  │ Room DB    │  └──────────────┘
│ Repo     │   │ DataSource  │  └────────────┘
└────┬─────┘   └──────┬──────┘
     │                │
     ↓                ↓
┌──────────┐   ┌──────────────┐
│ MCPServer│   │ github-mcp-  │
│          │   │   server     │
│ Аналог:  │   │   (Go)       │
│ Local    │   │              │
│ DataSrc  │   │ External     │
└──────────┘   │ Service      │
               └──────────────┘
```

---

## 📱 Сравнение с Android

| JavaScript | Android | Роль |
|-----------|---------|------|
| `index.js` | `MainActivity` | Entry point, запускает все |
| `MainServer` | `ViewModel + UseCases` | Бизнес-логика, orchestration |
| `MCPClient` | `Repository` | Посредник к локальным данным |
| `MCPServer` | `LocalDataSource` | Источник локальных данных |
| `GitHubMCPClient` | `RemoteDataSource` | Источник удаленных данных (GitHub) |
| `AppDatabase` | `Room Database` | SQLite БД |
| `count.json` | `SharedPreferences` | Простое хранилище |
| Express endpoints | `@RestController` | REST API |

---

## 🔄 Поток данных (Data Flow)

### Пример: Пользователь отправляет сообщение "Сколько сообщений?"

```
1. Android App
   ↓ POST /api/chat { message: "Сколько сообщений?" }
   
2. index.js (Express)
   ↓ app.post('/api/chat', ...) → mainServer.handleMessage()
   
3. MainServer
   ↓ getToolsForLLM() → [get_message_count, get_available_models]
   ↓ callLLM(message, tools) → YandexGPT
   
4. YandexGPT
   ↓ Анализирует → "USE_TOOL: get_message_count"
   
5. MainServer
   ↓ Парсит команду → activeMCPClient.callTool('get_message_count')
   
6. MCPClient
   ↓ mcpServer.callTool('get_message_count')
   
7. MCPServer
   ↓ Читает count.json → { models: { "L3-8B-Stheno": 42, ... } }
   ↓ Возвращает результат
   
8. MainServer
   ↓ Передает результат обратно в YandexGPT
   
9. YandexGPT
   ↓ Формирует финальный ответ: "У модели L3-8B-Stheno 42 сообщения"
   
10. index.js
    ↓ res.json({ success: true, message: "..." })
    
11. Android App
    ↓ Показывает ответ пользователю
```

---

## 🔧 Файлы и их роль

### 1. `index.js` - Главная точка входа

**Что делает:**
- Создает Express сервер
- Инициализирует все компоненты (DI)
- Определяет REST API endpoints
- Обрабатывает запросы от Android приложения

**Аналог в Android:**
```kotlin
class MainActivity : ComponentActivity() {
    private val database by inject<AppDatabase>()
    private val viewModel by viewModel<ChatViewModel>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Инициализация компонентов
    }
}
```

**Зависимости:**
- Создает: `AppDatabase`, `MCPServer`, `MCPClient`, `GitHubMCPClient`, `MainServer`
- Импортирует: все модули

---

### 2. `server/MainServer.js` - Оркестратор

**Что делает:**
- Управляет бизнес-логикой
- Вызывает YandexGPT с Tool Chaining
- Переключает между локальным и GitHub MCP
- Координирует взаимодействие всех компонентов

**Аналог в Android:**
```kotlin
class ChatViewModel(
    private val localRepo: LocalRepository,
    private val githubRepo: GitHubRepository,
    private val database: AppDatabase
) : ViewModel() {
    suspend fun handleMessage(message: String): Response {
        val tools = getAvailableTools()
        val response = llmService.call(message, tools)
        return response
    }
}
```

**Зависимости:**
- Принимает: `MCPClient`, `MCPServer`, `AppDatabase`, `GitHubMCPClient`
- Вызывает: YandexGPT API

**Ключевые методы:**
- `handleMessage()` - обработка сообщения от пользователя
- `callLLM()` - вызов YandexGPT с поддержкой инструментов
- `getToolsForLLM()` - получение списка доступных инструментов
- `setMCPMode()` - переключение между локальным/GitHub MCP

---

### 3. `mcp/MCPServer.js` - Локальный MCP сервер

**Что делает:**
- Предоставляет локальные инструменты (tools)
- Работает с `count.json` для хранения счетчиков
- Реализует логику инструментов

**Аналог в Android:**
```kotlin
class LocalDataSource {
    fun getMessageCount(modelName: String?): Int {
        val prefs = context.getSharedPreferences("count", MODE_PRIVATE)
        return prefs.getInt(modelName, 0)
    }
    
    fun getAvailableModels(): List<String> {
        return listOf("L3-8B-Stheno", "MiniMax-M2", ...)
    }
}
```

**Инструменты (Tools):**
1. `get_message_count` - получить счетчик сообщений
2. `get_available_models` - получить список моделей

**Зависимости:**
- Читает/пишет: `count.json`
- Никого не импортирует (самостоятельный модуль)

---

### 4. `mcp/MCPClient.js` - Репозиторий для локального MCP

**Что делает:**
- Посредник между `MainServer` и `MCPServer`
- Простой wrapper без дополнительной логики

**Аналог в Android:**
```kotlin
class LocalRepository(
    private val localDataSource: LocalDataSource
) {
    suspend fun getMessageCount() = localDataSource.getMessageCount()
    suspend fun listTools() = localDataSource.listTools()
}
```

**Зависимости:**
- Принимает: `MCPServer`
- Делегирует все вызовы в `MCPServer`

---

### 5. `mcp/GitHubMCPClient.js` - GitHub API клиент

**Что делает:**
- Подключается к GitHub MCP Server (Go binary)
- Запускает процесс через `StdioClientTransport`
- Вызывает GitHub инструменты (~40 штук)

**Аналог в Android:**
```kotlin
class GitHubRepository(
    private val githubApiService: GitHubApiService
) {
    suspend fun searchRepositories(query: String) = 
        githubApiService.searchRepos(query)
    
    suspend fun listPullRequests(owner: String, repo: String) = 
        githubApiService.listPRs(owner, repo)
}
```

**Инструменты GitHub MCP:**
- `github_search_repositories`
- `github_list_pull_requests`
- `github_create_issue`
- и еще ~37 инструментов

**Зависимости:**
- Запускает: `github-mcp-server` (Go binary)
- Использует: `@modelcontextprotocol/sdk`

---

### 6. `database/AppDatabase.js` - SQLite БД

**Что делает:**
- Создает и управляет SQLite базой данных
- Хранит: сообщения, счетчики моделей, PR комментарии

**Аналог в Android:**
```kotlin
@Database(entities = [Message::class, ModelCounter::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun counterDao(): CounterDao
}
```

**Таблицы:**
1. `messages` - все сообщения из приложения
2. `model_counters` - счетчики для моделей
3. `github_pr_comments` - комментарии к PR
4. `github_pr_summaries` - суммаризации PR

**Зависимости:**
- Использует: `better-sqlite3` (аналог Room)

---

## 🚀 Запуск проекта

### 1. Установка зависимостей:
```bash
cd mcp-proxy
npm install
```

### 2. Запуск сервера:
```bash
npm start          # Обычный запуск
npm run dev        # С автоперезапуском при изменениях
```

### 3. Проверка здоровья:
```bash
curl http://localhost:3001/health
```

---

## 🧪 Тестирование

### Отправка сообщения:
```bash
curl -X POST http://localhost:3001/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Сколько сообщений?",
    "history": []
  }'
```

### Переключение на GitHub MCP:
```bash
curl -X POST http://localhost:3001/api/mcp-mode \
  -H "Content-Type: application/json" \
  -d '{
    "useGitHub": true,
    "githubToken": "ghp_your_token_here"
  }'
```

---

## 🎓 Ключевые концепции

### 1. **Dependency Injection (DI)**
```javascript
// JavaScript (Manual DI)
const database = new AppDatabase();
const mcpServer = new MCPServer();
const mcpClient = new MCPClient(mcpServer);
const mainServer = new MainServer(mcpClient, mcpServer, database, ...);
```

```kotlin
// Android (Hilt)
@Module
object AppModule {
    @Provides fun provideDatabase() = AppDatabase()
    @Provides fun provideMCPServer() = MCPServer()
    @Provides fun provideMCPClient(server: MCPServer) = MCPClient(server)
}
```

### 2. **Repository Pattern**
```javascript
// MCPClient - это Repository
class MCPClient {
    async listTools() {
        return this.mcpServer.listTools();
    }
}
```

```kotlin
// Android Repository
class LocalRepository(private val dataSource: LocalDataSource) {
    suspend fun getTools() = dataSource.getTools()
}
```

### 3. **Clean Architecture Layers**

```
┌─────────────────────────────────────┐
│  Presentation (Android App)         │ ← React/Compose UI
└─────────────────┬───────────────────┘
                  │
┌─────────────────▼───────────────────┐
│  API Layer (index.js)               │ ← Express REST API
└─────────────────┬───────────────────┘
                  │
┌─────────────────▼───────────────────┐
│  Domain (MainServer)                │ ← Business Logic
└────┬─────────────┬──────────────────┘
     │             │
┌────▼─────┐  ┌───▼──────┐
│  Data    │  │ External │
│ (Repos)  │  │ (APIs)   │
└──────────┘  └──────────┘
```

---

## 💡 Советы для понимания

1. **`index.js` = MainActivity** - стартовая точка, создает все
2. **`MainServer` = ViewModel** - orchestration, бизнес-логика
3. **`MCPClient` = Repository** - посредник к данным
4. **`MCPServer` = DataSource** - источник данных
5. **`AppDatabase` = Room** - база данных

**Главное правило:** Один класс = один файл = одна ответственность! 🎯

---

## 📚 Дополнительно

- **Старые файлы:** `localserver.js` и `mcpserver.js` теперь не используются
- **Команды:** `npm run old-server` и `npm run old-mcp` для запуска старых версий
- **Миграция:** Все функционал сохранен, просто разбит на модули




