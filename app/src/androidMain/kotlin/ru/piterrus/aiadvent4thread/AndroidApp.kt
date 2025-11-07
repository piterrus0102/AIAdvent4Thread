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
    
    // Навигация
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }
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
        is Screen.Chat -> {
            ChatScreenUI(
                messages = messages,
                currentMessage = currentMessage,
                isLoading = isLoading,
                responseMode = responseMode,
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
                onSendMessage = {
                    if (currentMessage.isNotBlank()) {
                        val userMessage = currentMessage
                        currentMessage = ""
                        
                        // Добавляем сообщение пользователя
                        val newUserMsg = ChatMessage(
                            text = userMessage,
                            isUser = true,
                            responseMode = responseMode
                        )
                        
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                // Сохраняем в БД и обновляем с правильным id
                                val userId = repository.saveMessage(newUserMsg)
                                messages = messages + newUserMsg.copy(id = userId)
                                
                                // Формируем историю для API (БЕЗ последнего добавленного сообщения,
                                // т.к. оно будет передано через userMessage)
                                val apiMessageHistory = messages.map { chatMsg ->
                                    Message(
                                        role = if (chatMsg.isUser) "user" else "assistant",
                                        text = chatMsg.text
                                    )
                                }
                                
                                // Отправляем с полной историей
                                val result = gptClient.sendMessage(
                                    userMessage = userMessage,
                                    messageHistory = apiMessageHistory,
                                    responseMode = responseMode
                                )
                                
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
                    currentScreen = Screen.Chat
                    shouldScrollToBottom = true
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
    }
}

// Sealed class для навигации
sealed class Screen {
    object Chat : Screen()
    data class SearchResults(val messageId: Long) : Screen()
    object Discussion : Screen()
    data class ExpertDetail(val expert: ExpertRole, val expertNumber: Int) : Screen()
}

