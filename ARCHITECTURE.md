# Архитектура проекта

Проект полностью реструктурирован по принципам чистой архитектуры с разделением на слои.

## Структура проекта

```
app/src/commonMain/kotlin/ru/piterrus/aiadvent4thread/
├── data/                          # DATA СЛОЙ
│   ├── client/                    # Клиенты для работы с API
│   │   ├── YandexGPTClient.kt    # Клиент YandexGPT
│   │   └── HuggingFaceClient.kt  # Клиент HuggingFace
│   └── model/                     # Модели данных
│       ├── ApiModels.kt           # Общие модели API
│       ├── YandexGPTModels.kt    # Модели YandexGPT
│       ├── HuggingFaceModels.kt  # Модели HuggingFace
│       ├── DiscussionModels.kt   # Модели дискуссий
│       └── Prompts.kt            # Промпты для LLM
│
└── presentation/                  # PRESENTATION СЛОЙ
    ├── start/                     # Стартовый экран
    │   ├── StartScreen.kt        # UI
    │   ├── StartScreenState.kt   # State
    │   ├── StartScreenIntent.kt  # Intent
    │   └── StartScreenViewModel.kt # ViewModel
    │
    ├── chat/                      # Чат экран
    │   ├── ChatScreen.kt
    │   ├── ChatScreenState.kt
    │   ├── ChatScreenIntent.kt
    │   └── ChatScreenViewModel.kt
    │
    ├── huggingface/               # HuggingFace экран
    │   ├── HuggingFaceScreen.kt
    │   ├── HuggingFaceScreenState.kt
    │   ├── HuggingFaceScreenIntent.kt
    │   └── HuggingFaceScreenViewModel.kt
    │
    ├── discussion/                # Экран дискуссии
    │   ├── DiscussionScreen.kt
    │   ├── DiscussionScreenState.kt
    │   ├── DiscussionScreenIntent.kt
    │   └── DiscussionScreenViewModel.kt
    │
    ├── search/                    # Экран результатов поиска
    │   ├── SearchResultsScreen.kt
    │   ├── SearchResultsScreenState.kt
    │   ├── SearchResultsScreenIntent.kt
    │   └── SearchResultsScreenViewModel.kt
    │
    ├── temperature/               # Детальный экран температуры
    │   ├── TemperatureDetailScreen.kt
    │   ├── TemperatureDetailScreenState.kt
    │   ├── TemperatureDetailScreenIntent.kt
    │   └── TemperatureDetailScreenViewModel.kt
    │
    └── expert/                    # Детальный экран эксперта
        ├── ExpertDetailScreen.kt
        ├── ExpertDetailScreenState.kt
        ├── ExpertDetailScreenIntent.kt
        └── ExpertDetailScreenViewModel.kt
```

## Принципы архитектуры

### 1. Разделение на слои

#### Data слой (`data/`)
- **Ответственность**: Работа с данными, API, базой данных
- **Содержит**:
  - `client/` - Клиенты для работы с внешними API (YandexGPT, HuggingFace)
  - `model/` - Модели данных, результаты API, промпты

#### Presentation слой (`presentation/`)
- **Ответственность**: UI, взаимодействие с пользователем, бизнес-логика экранов
- **Содержит**: Для каждого экрана отдельная папка с:
  - `Screen.kt` - UI компонент (Composable функция)
  - `State.kt` - Data class с состоянием экрана
  - `Intent.kt` - Sealed interface с возможными действиями пользователя
  - `ViewModel.kt` - Класс с логикой обработки интентов

### 2. Паттерн MVI (Model-View-Intent)

Каждый экран следует паттерну MVI:

```kotlin
// 1. State - состояние экрана
data class ChatScreenState(
    val messages: List<ChatMessage> = emptyList(),
    val currentMessage: String = "",
    val isLoading: Boolean = false,
    // ...
)

// 2. Intent - действия пользователя
sealed interface ChatScreenIntent {
    data class MessageChanged(val message: String) : ChatScreenIntent
    object SendMessage : ChatScreenIntent
    object ClearHistory : ChatScreenIntent
    // ...
}

// 3. ViewModel - обработка интентов и управление состоянием
class ChatScreenViewModel(...) {
    private val _state = MutableStateFlow(ChatScreenState())
    val state: StateFlow<ChatScreenState> = _state.asStateFlow()
    
    fun intentToAction(intent: ChatScreenIntent) {
        when (intent) {
            is ChatScreenIntent.MessageChanged -> {
                _state.update { it.copy(currentMessage = intent.message) }
            }
            is ChatScreenIntent.SendMessage -> {
                sendMessage()
            }
            // ...
        }
    }
}

// 4. Screen - UI принимает state и onIntent
@Composable
fun ChatScreen(
    state: ChatScreenState,
    onIntent: (ChatScreenIntent) -> Unit
) {
    // UI использует state для отображения
    // И вызывает onIntent при действиях пользователя
    TextField(
        value = state.currentMessage,
        onValueChange = { onIntent(ChatScreenIntent.MessageChanged(it)) }
    )
}
```

### 3. Зависимости

**ViewModel получает через конструктор:**
- Data layer клиенты: `YandexGPTClient`, `HuggingFaceClient`
- Repository: `IChatRepository` - для работы с БД
- `CoroutineScope` - для асинхронных операций
- Навигационные коллбэки: `onNavigateToX`

**ViewModel сам управляет:**
- Загрузкой данных из БД (через Flow в init блоке)
- Сохранением данных в БД
- Всей бизнес-логикой экрана

**Screen зависит только от:**
- `State` - состояние экрана
- `onIntent` - функция для отправки интентов

**Все данные идут через State, все действия через Intent**

### 4. Управление зависимостями (Koin DI)

Приложение использует **Koin** - легковесный фреймворк для dependency injection.

#### Структура DI:

**`di/AppModule.kt`** - Главный модуль с определением зависимостей:

```kotlin
val appModule = module {
    // Data Layer - Clients
    single { YandexGPTClient(...) }
    single { HuggingFaceClient(...) }
    
    // Data Layer - Database & Repository
    single { ChatDatabase.getDatabase(get()) }
    single<IChatRepository> { ChatRepository(...) }
    
    // Android-specific
    single { PreferencesManager(get()) }
    single { CoroutineScope(Dispatchers.Main) }
    
    // Presentation Layer - ViewModels
    viewModel { (params) -> StartScreenViewModel(...) }
    viewModel { (params) -> ChatScreenViewModel(...) }
    // ...
}
```

**`AIAdventApplication.kt`** - Инициализация Koin:

```kotlin
class AIAdventApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AIAdventApplication)
            modules(appModule)
        }
    }
}
```

**`AndroidApp.kt`** - Использование `koinViewModel()`:

```kotlin
@Composable
fun App() {
    when (screen) {
        is Screen.Start -> {
            val viewModel: StartScreenViewModel = koinViewModel {
                parametersOf(
                    { mode -> /* navigate */ },
                    { /* navigate */ }
                )
            }
            // ...
        }
    }
}
```

#### Преимущества Koin:
- ✅ **Автоматическая инъекция** - не нужно создавать зависимости вручную
- ✅ **Lifecycle-aware** - ViewModels автоматически очищаются
- ✅ **Поддержка параметров** - легко передавать навигационные коллбэки
- ✅ **Простота тестирования** - легко подменять зависимости в тестах
- ✅ **Централизация** - все зависимости в одном месте

### 5. Навигация

Навигация управляется в `AndroidApp.kt`:
- Sealed class `Screen` описывает все возможные экраны
- State переменная `currentScreen` управляет текущим экраном
- ViewModels создаются через `koinViewModel()` с параметрами
- ViewModels получают навигационные коллбэки как параметры при создании
- Koin автоматически инжектирует все зависимости (clients, repository, scope)

### 6. Поток данных (Data Flow)

```
┌─────────────┐
│   Database  │
└──────┬──────┘
       │ Flow
       ▼
┌─────────────┐     init {     ┌─────────────┐
│ Repository  │────repository────▶│  ViewModel  │
└─────────────┘   .collect { }  └──────┬──────┘
                                       │
                                       │ StateFlow
                                       ▼
                                ┌─────────────┐
                                │   Screen    │
                                │     UI      │
                                └──────┬──────┘
                                       │
                                       │ onIntent
                                       ▼
                                ┌─────────────┐
                                │  ViewModel  │
                                │intentToAction│
                                └──────┬──────┘
                                       │
                                       │ repository.save()
                                       ▼
                                ┌─────────────┐
                                │ Repository  │
                                └──────┬──────┘
                                       │
                                       ▼
                                ┌─────────────┐
                                │   Database  │
                                └─────────────┘
```

**Ключевые моменты:**
1. ViewModel подписывается на `repository.allMessages` в `init` блоке
2. При изменении БД → Flow автоматически обновляет State
3. UI отображает State и отправляет Intent
4. ViewModel обрабатывает Intent и сохраняет в БД
5. Цикл замыкается: БД → Flow → State → UI → Intent → ViewModel → БД

**UI НИКОГДА не работает с БД напрямую!**

## Примеры использования

### Добавление нового экрана

1. Создайте папку в `presentation/` с именем экрана
2. Создайте 4 файла:
   - `YourScreenState.kt` - data class с полями состояния
   - `YourScreenIntent.kt` - sealed interface с действиями
   - `YourScreenViewModel.kt` - класс с методом `intentToAction`
   - `YourScreen.kt` - Composable функция с параметрами `state` и `onIntent`

3. Добавьте экран в `Screen` sealed class в `AndroidApp.kt`
4. Создайте ViewModel и добавьте обработку в when блок в `AndroidApp.kt`

### Взаимодействие с Data слоем

ViewModel получает клиенты через конструктор (автоматически через Koin):

```kotlin
class MyViewModel(
    private val gptClient: YandexGPTClient,  // Инжектится Koin
    private val hfClient: HuggingFaceClient,  // Инжектится Koin
    private val repository: IChatRepository,  // Инжектится Koin
    private val coroutineScope: CoroutineScope, // Инжектится Koin
    onNavigateBack: () -> Unit  // Параметр из parametersOf()
) {
    // Используйте клиенты для работы с API
}
```

Koin автоматически находит и предоставляет все зависимости, определенные в `appModule`.

## Преимущества архитектуры

1. **Чистое разделение ответственностей** - каждый слой имеет четко определенную роль
2. **Тестируемость** - легко тестировать ViewModels и бизнес-логику
3. **Масштабируемость** - легко добавлять новые экраны
4. **Поддерживаемость** - понятная структура, легко найти нужный код
5. **Однонаправленный поток данных** - State → UI → Intent → ViewModel → State
6. **Изолированность UI** - Screen не содержит бизнес-логики, только отображение

## Технологии

- **Kotlin Multiplatform** - кросс-платформенный код
- **Compose Multiplatform** - UI
- **Kotlin Coroutines & Flow** - асинхронность и реактивность
- **StateFlow** - управление состоянием
- **Ktor** - HTTP клиент для API
- **Koin** - Dependency Injection фреймворк
- **Room** - локальная база данных

