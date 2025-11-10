package ru.piterrus.aiadvent4thread

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import ru.piterrus.aiadvent4thread.database.ChatDatabase
import ru.piterrus.aiadvent4thread.database.ChatRepository

@Composable
actual fun App(
    defaultApiKey: String,
    defaultFolderId: String
) {
    val context = LocalContext.current
    AppContent(
        defaultApiKey = defaultApiKey,
        defaultFolderId = defaultFolderId
    ) { apiKey, catalogId ->
        ChatScreenWithDatabase(
            context = context,
            apiKey = apiKey,
            catalogId = catalogId
        )
    }
}

@Composable
fun ChatScreenWithDatabase(
    context: Context,
    apiKey: String,
    catalogId: String
) {
    // Создаем базу данных и repository
    val database = remember { ChatDatabase.getDatabase(context) }
    val repository = remember { 
        ChatRepository(
            database.chatMessageDao(),
            database.searchResultDao()
        ) 
    }
    
    // Менеджер настроек
    val prefsManager = remember { PreferencesManager(context) }
    
    // Загружаем сообщения из БД
    val messagesFromDb by repository.allMessages.collectAsState(initial = emptyList())
    
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var currentMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var responseMode by remember { mutableStateOf(prefsManager.responseMode) }
    
    // Накопительные списки для каждой температуры
    var temperature0Responses by remember { mutableStateOf(listOf<String>()) }
    var temperature05Responses by remember { mutableStateOf(listOf<String>()) }
    var temperature1Responses by remember { mutableStateOf(listOf<String>()) }
    
    // Результат анализа от GPT
    var similarityAnalysis by remember { mutableStateOf<String?>(null) }
    
    // Навигация
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Start) }
    var shouldScrollToBottom by remember { mutableStateOf(false) }
    
    // Состояние экрана дискуссии для сохранения позиции скролла
    var discussionState by remember { mutableStateOf<DiscussionState?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    val gptClient = remember { YandexGPTClient(apiKey, catalogId) }
    
    // Синхронизируем состояние с БД
    LaunchedEffect(messagesFromDb) {
        if (messages.isEmpty() && messagesFromDb.isNotEmpty()) {
            messages = messagesFromDb
        }
    }
    
    when (currentScreen) {
        is Screen.Start -> {
            StartScreen(
                onModeSelected = { mode ->
                    responseMode = mode
                    prefsManager.responseMode = mode
                    currentScreen = Screen.Chat
                },
                onDiscussionSelected = {
                    currentScreen = Screen.Discussion
                }
            )
        }
        
        is Screen.Chat -> {
            ChatScreenUI(
                messages = messages,
                currentMessage = currentMessage,
                isLoading = isLoading,
                responseMode = responseMode,
                similarityAnalysis = similarityAnalysis,
                onResponseModeToggle = { 
                    responseMode = it
                    prefsManager.responseMode = it
                },
                onMessageChange = { currentMessage = it },
                shouldScrollToBottom = shouldScrollToBottom,
                onScrolledToBottom = { shouldScrollToBottom = false },
                onNavigateToDiscussion = {
                    currentScreen = Screen.Discussion
                },
                onBackToStart = {
                    currentScreen = Screen.Start
                },
                onSendMessage = {
                    println("🚀 onSendMessage вызван! Режим: $responseMode, Сообщение: $currentMessage")
                    if (currentMessage.isNotBlank()) {
                        val userMessage = currentMessage
                        currentMessage = ""
                        
                        // Очищаем предыдущий анализ при отправке нового сообщения
                        similarityAnalysis = null
                        
                        // Добавляем сообщение пользователя
                        val newUserMsg = ChatMessage(
                            text = userMessage,
                            isUser = true,
                            responseMode = responseMode
                        )
                        
                        println("📝 Устанавливаем isLoading = true")
                        isLoading = true
                        coroutineScope.launch {
                            println("🔄 Начинаем корутину отправки")
                            try {
                                // Сохраняем в БД и обновляем с правильным id
                                val userId = repository.saveMessage(newUserMsg)
                                messages = messages + newUserMsg.copy(id = userId)
                                
                                // Формируем историю для API (БЕЗ последнего добавленного сообщения,
                                // т.к. оно будет передано через userMessage)
                                // Для режима сравнения температур не передаем историю, чтобы ускорить запросы
                                val apiMessageHistory = if (responseMode == ResponseMode.TEMPERATURE_COMPARISON) {
                                    emptyList()
                                } else {
                                    messages.map { chatMsg ->
                                        Message(
                                            role = if (chatMsg.isUser) "user" else "assistant",
                                            text = chatMsg.text
                                        )
                                    }
                                }
                                
                                // Отправляем с полной историей
                                val result = if (responseMode == ResponseMode.TEMPERATURE_COMPARISON) {
                                    gptClient.sendMessageWithTemperatureComparison(
                                        userMessage = userMessage,
                                        messageHistory = apiMessageHistory
                                    )
                                } else {
                                    gptClient.sendMessage(
                                        userMessage = userMessage,
                                        messageHistory = apiMessageHistory,
                                        responseMode = responseMode
                                    )
                                }
                                
                                when (result) {
                                    is ApiResult.Success -> {
                                        when (val response = result.data) {
                                            is MessageResponse.StandardResponse -> {
                                                // Обычный ответ
                                                val assistantMsg = ChatMessage(
                                                    text = response.text,
                                                    isUser = false,
                                                    responseMode = ResponseMode.DEFAULT
                                                )
                                                val messageId = repository.saveMessage(assistantMsg)
                                                // Обновляем сообщение с правильным id
                                                messages = messages + assistantMsg.copy(id = messageId)
                                            }
                                            is MessageResponse.FixedResponse -> {
                                                // Ответ с результатами поиска или задачами
                                                val assistantMsg = ChatMessage(
                                                    text = "Получено результатов: ${response.results.size}",
                                                    isUser = false,
                                                    responseMode = responseMode,
                                                    rawResponse = response.rawText
                                                )
                                                val messageId = repository.saveMessage(assistantMsg)
                                                // Обновляем сообщение с правильным id
                                                messages = messages + assistantMsg.copy(id = messageId)
                                                
                                                // Сохраняем результаты поиска
                                                repository.saveSearchResults(messageId, response.results)
                                                
                                                // Автоматически переходим на экран результатов
                                                currentScreen = Screen.SearchResults(messageId)
                                            }
                                            is MessageResponse.TemperatureComparisonResponse -> {
                                                // Ответ с результатами сравнения температур
                                                val assistantMsg = ChatMessage(
                                                    text = "Получено ${response.results.size} ответов с разными температурами",
                                                    isUser = false,
                                                    responseMode = ResponseMode.TEMPERATURE_COMPARISON,
                                                    temperatureResults = response.results
                                                )
                                                val messageId = repository.saveMessage(assistantMsg)
                                                // Обновляем сообщение с правильным id
                                                messages = messages + assistantMsg.copy(id = messageId)
                                                
                                                // Добавляем результаты в накопительные списки
                                                response.results.forEach { result ->
                                                    when (result.temperature) {
                                                        0.0 -> temperature0Responses = temperature0Responses + result.text
                                                        0.5 -> temperature05Responses = temperature05Responses + result.text
                                                        1.0 -> temperature1Responses = temperature1Responses + result.text
                                                    }
                                                }
                                                
                                                // Отправляем запрос на анализ ТОЛЬКО если есть хотя бы 2 ответа для сравнения
                                                if (temperature0Responses.size < 2) {
                                                    println("📊 Пропускаем анализ - нужно минимум 2 ответа для сравнения (сейчас: ${temperature0Responses.size})")
                                                    similarityAnalysis = null
                                                } else {
                                                    println("📊 Отправляем запрос на анализ совпадений (всего ответов: ${temperature0Responses.size})...")
                                                    val analysisPrompt = buildString {
                                                        appendLine("Ты — анализатор стабильности языковой модели.")
                                                        appendLine()
                                                        appendLine("На вход тебе подаются тексты, сгенерированные одним и тем же запросом,")
                                                        appendLine("но при разных значениях температуры (например: 0.0, 0.5, 1.0)")
                                                        appendLine("и за несколько итераций (несколько запусков одного и того же вопроса).")
                                                        appendLine()
                                                        appendLine("Твоя задача:")
                                                        appendLine("1. Для каждой температуры рассматривать все её ответы как отдельную группу.")
                                                        appendLine("   Например:")
                                                        appendLine("   temperature_0.0 → [ответ1, ответ2, ответ3, ...]")
                                                        appendLine("   temperature_0.5 → [ответ1, ответ2, ответ3, ...]")
                                                        appendLine("   temperature_1.0 → [ответ1, ответ2, ответ3, ...]")
                                                        appendLine()
                                                        appendLine("2. Внутри каждой группы сравни каждый ответ со всеми другими (полный перебор всех возможных пар, включая непоследовательные).")
                                                        appendLine("   То есть если есть N ответов, то нужно посчитать N×(N−1)/2 сравнений.")
                                                        appendLine()
                                                        appendLine("3. Для каждой пары ответов выполни посимвольное сравнение (в лоб, без нормализации и семантики):")
                                                        appendLine("   - Считай количество полностью совпавших символов (включая пробелы, переносы строк, пунктуацию).")
                                                        appendLine("   - Раздели количество совпавших символов на длину самого длинного из двух ответов.")
                                                        appendLine("   - Умножь результат на 100 — это процент совпадения для пары.")
                                                        appendLine()
                                                        appendLine("4. Усредни полученные проценты по всем парам внутри группы.")
                                                        appendLine()
                                                        appendLine("⚙️ Правила:")
                                                        appendLine("- Сравнение строго посимвольное.")
                                                        appendLine("- Не учитывать смысл, регистр, пунктуацию, или переносы строк.")
                                                        appendLine("- Не выполнять умных преобразований текста.")
                                                        appendLine("- Если ответы разной длины — использовать длину самого длинного при расчёте процента.")
                                                        appendLine("- Не добавлять свои комментарии, кроме итогового анализа.")
                                                        appendLine()
                                                        appendLine("=== ДАННЫЕ ===")
                                                        appendLine()
                                                        appendLine("{")
                                                        
                                                        append("  \"temperature_0.0\": [")
                                                        temperature0Responses.forEachIndexed { index, text ->
                                                            if (index > 0) append(", ")
                                                            append("\"${text.replace("\"", "\\\"")}\"")
                                                        }
                                                        appendLine("],")
                                                        
                                                        append("  \"temperature_0.5\": [")
                                                        temperature05Responses.forEachIndexed { index, text ->
                                                            if (index > 0) append(", ")
                                                            append("\"${text.replace("\"", "\\\"")}\"")
                                                        }
                                                        appendLine("],")
                                                        
                                                        append("  \"temperature_1.0\": [")
                                                        temperature1Responses.forEachIndexed { index, text ->
                                                            if (index > 0) append(", ")
                                                            append("\"${text.replace("\"", "\\\"")}\"")
                                                        }
                                                        appendLine("]")
                                                        
                                                        appendLine("}")
                                                        appendLine()
                                                        appendLine("Формат вывода (строго следуй):")
                                                        appendLine("Температура 0.0: XX% - характеристика")
                                                        appendLine("Температура 0.5: XX% - характеристика")
                                                        appendLine("Температура 1.0: XX% - характеристика")
                                                    }
                                                
                                                    val analysisResult = gptClient.sendMessage(
                                                        userMessage = analysisPrompt,
                                                        messageHistory = emptyList(),
                                                        responseMode = ResponseMode.DEFAULT,
                                                        temperature = 0.3
                                                    )
                                                    
                                                    when (analysisResult) {
                                                        is ApiResult.Success -> {
                                                            val analysisResponse = analysisResult.data as? MessageResponse.StandardResponse
                                                            if (analysisResponse != null) {
                                                                similarityAnalysis = analysisResponse.text
                                                                println("✅ Анализ получен: ${analysisResponse.text}")
                                                            }
                                                        }
                                                        is ApiResult.Error -> {
                                                            similarityAnalysis = "Ошибка анализа: ${analysisResult.message}"
                                                            println("❌ Ошибка анализа: ${analysisResult.message}")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    is ApiResult.Error -> {
                                        // Ошибка
                                        val errorMsg = ChatMessage(
                                            text = result.message,
                                            isUser = false,
                                            responseMode = ResponseMode.DEFAULT
                                        )
                                        val messageId = repository.saveMessage(errorMsg)
                                        messages = messages + errorMsg.copy(id = messageId)
                                    }
                                }
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                },
                onClearHistory = {
                    messages = emptyList()
                    coroutineScope.launch {
                        repository.clearHistory()
                    }
                },
                onMessageClick = { message ->
                    // Переход на экран результатов при клике на сообщение
                    if (!message.isUser && (message.responseMode == ResponseMode.FIXED_RESPONSE_ENABLED || message.responseMode == ResponseMode.TASK) && message.id > 0) {
                        currentScreen = Screen.SearchResults(message.id)
                    }
                },
                onTemperatureResultClick = { message, index ->
                    // Переход на экран детального просмотра температурного результата
                    if (message.temperatureResults != null && index < message.temperatureResults.size) {
                        currentScreen = Screen.TemperatureDetail(message.temperatureResults[index])
                    }
                }
            )
        }
        
        is Screen.SearchResults -> {
            val messageId = (currentScreen as Screen.SearchResults).messageId
            val searchResults by repository.getSearchResults(messageId).collectAsState(initial = emptyList())
            
            // Получаем сообщение с raw response
            var rawResponse by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(messageId) {
                rawResponse = repository.getMessageById(messageId)?.rawResponse
            }
            
            SearchResultsScreen(
                results = searchResults,
                rawResponse = rawResponse,
                onBackClick = {
                    currentScreen = Screen.Chat
                    shouldScrollToBottom = true
                }
            )
        }
        
        is Screen.Discussion -> {
            DiscussionScreen(
                onBackClick = {
                    currentScreen = Screen.Start
                },
                gptClient = gptClient,
                onExpertClick = { expert, expertNumber ->
                    currentScreen = Screen.ExpertDetail(expert, expertNumber)
                },
                savedState = discussionState,
                onStateChange = { state ->
                    discussionState = state
                }
            )
        }
        
        is Screen.ExpertDetail -> {
            val expertDetail = currentScreen as Screen.ExpertDetail
            ExpertDetailScreen(
                expert = expertDetail.expert,
                expertNumber = expertDetail.expertNumber,
                onBackClick = {
                    currentScreen = Screen.Discussion
                }
            )
        }
        
        is Screen.TemperatureDetail -> {
            val temperatureDetail = currentScreen as Screen.TemperatureDetail
            TemperatureDetailScreen(
                temperatureResult = temperatureDetail.temperatureResult,
                onBackClick = {
                    currentScreen = Screen.Chat
                    shouldScrollToBottom = true
                }
            )
        }
    }
}

// Sealed class для навигации
sealed class Screen {
    object Start : Screen()
    object Chat : Screen()
    data class SearchResults(val messageId: Long) : Screen()
    object Discussion : Screen()
    data class ExpertDetail(val expert: ExpertRole, val expertNumber: Int) : Screen()
    data class TemperatureDetail(val temperatureResult: TemperatureResult) : Screen()
}

