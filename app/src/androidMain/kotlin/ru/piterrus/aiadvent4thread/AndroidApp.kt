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
    val repository = remember { ChatRepository(database.chatMessageDao()) }
    
    // Загружаем сообщения из БД
    val messagesFromDb by repository.allMessages.collectAsState(initial = emptyList())
    
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var currentMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    val gptClient = remember { YandexGPTClient(apiKey, catalogId) }
    
    // Синхронизируем состояние с БД
    LaunchedEffect(messagesFromDb) {
        if (messages.isEmpty() && messagesFromDb.isNotEmpty()) {
            messages = messagesFromDb
        }
    }
    
    // История для API
    val apiMessageHistory = remember(messages) {
        messages.map { chatMsg ->
            Message(
                role = if (chatMsg.isUser) "user" else "assistant",
                text = chatMsg.text
            )
        }
    }
    
    ChatScreenUI(
        messages = messages,
        currentMessage = currentMessage,
        isLoading = isLoading,
        onMessageChange = { currentMessage = it },
        onSendMessage = {
            if (currentMessage.isNotBlank()) {
                val userMessage = currentMessage
                currentMessage = ""
                
                // Добавляем сообщение пользователя
                val newUserMsg = ChatMessage(userMessage, true)
                messages = messages + newUserMsg
                
                // Сохраняем в БД
                coroutineScope.launch {
                    repository.saveMessage(newUserMsg)
                }
                
                isLoading = true
                coroutineScope.launch {
                    try {
                        // Отправляем с полной историей
                        val response = gptClient.sendMessage(
                            userMessage = userMessage,
                            messageHistory = apiMessageHistory
                        )
                        
                        // Добавляем ответ ассистента
                        val assistantMsg = ChatMessage(response, false)
                        messages = messages + assistantMsg
                        
                        // Сохраняем в БД
                        repository.saveMessage(assistantMsg)
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
        }
    )
}

