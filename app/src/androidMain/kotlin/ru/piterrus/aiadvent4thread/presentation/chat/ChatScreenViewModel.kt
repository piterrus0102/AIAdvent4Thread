package ru.piterrus.aiadvent4thread.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.piterrus.aiadvent4thread.data.client.YandexGPTClient
import ru.piterrus.aiadvent4thread.data.model.*
import ru.piterrus.aiadvent4thread.data.repository.IChatRepository

class ChatScreenViewModel(
    private val gptClient: YandexGPTClient,
    private val repository: IChatRepository,
    initialResponseMode: ResponseMode = ResponseMode.DEFAULT
) : ViewModel() {
    private val _state = MutableStateFlow(ChatScreenState(responseMode = initialResponseMode))
    val state: StateFlow<ChatScreenState> = _state.asStateFlow()
    
    private val _commandFlow = MutableSharedFlow<ChatScreenCommand>(extraBufferCapacity = 1)
    val commandFlow: SharedFlow<ChatScreenCommand> = _commandFlow.asSharedFlow()
    
    init {
        // Загружаем сообщения только для текущего режима
        viewModelScope.launch {
            repository.getMessagesByMode(initialResponseMode).collect { messages ->
                _state.update { it.copy(messages = messages) }
            }
        }
    }
    
    fun intentToAction(intent: ChatScreenIntent) {
        when (intent) {
            is ChatScreenIntent.MessageChanged -> {
                _state.update { it.copy(currentMessage = intent.message) }
            }
            
            is ChatScreenIntent.SendMessage -> {
                sendMessage()
            }
            
            is ChatScreenIntent.SendContextPadding -> {
                sendContextPadding(intent.tokens)
            }
            
            is ChatScreenIntent.ClearHistory -> {
                viewModelScope.launch {
                    // Очищаем историю только для текущего режима
                    repository.clearHistoryForMode(_state.value.responseMode)
                    _state.update { 
                        it.copy(
                            messages = emptyList(),
                            temperature0Responses = emptyList(),
                            temperature05Responses = emptyList(),
                            temperature1Responses = emptyList(),
                            similarityAnalysis = null
                        )
                    }
                }
            }
            
            is ChatScreenIntent.ResponseModeToggle -> {
                _state.update { it.copy(responseMode = intent.mode) }
            }
            
            is ChatScreenIntent.MessageClicked -> {
                if (!intent.message.isUser && 
                    (intent.message.responseMode == ResponseMode.FIXED_RESPONSE_ENABLED || 
                     intent.message.responseMode == ResponseMode.TASK) && 
                    intent.message.id > 0) {
                    viewModelScope.launch {
                        _commandFlow.emit(ChatScreenCommand.NavigateToSearchResults(intent.message.id))
                    }
                }
            }
            
            is ChatScreenIntent.TemperatureResultClicked -> {
                if (intent.message.temperatureResults != null && 
                    intent.index < intent.message.temperatureResults.size) {
                    viewModelScope.launch {
                        _commandFlow.emit(ChatScreenCommand.NavigateToTemperatureDetail(intent.message.temperatureResults[intent.index]))
                    }
                }
            }
            
            is ChatScreenIntent.BackToStart -> {
                viewModelScope.launch {
                    _commandFlow.emit(ChatScreenCommand.NavigateToStart)
                }
            }
            
            is ChatScreenIntent.ScrolledToBottom -> {
                _state.update { it.copy(shouldScrollToBottom = false) }
            }
            
            is ChatScreenIntent.CopyMessageText -> {
                viewModelScope.launch {
                    _commandFlow.emit(ChatScreenCommand.ShowCopiedSnackbar("Текст скопирован"))
                }
            }
        }
    }
    
    private fun sendContextPadding(paddingTokens: Int) {
        // Создаем padding сообщение размером ~N токенов
        val approxCharsPerToken = 4
        val desiredChars = (paddingTokens * approxCharsPerToken).coerceAtMost(200_000)
        
        val chunk = "Стояло дерево. Смеркалось. Горела лампа "
        val paddingText = buildString(desiredChars) {
            while (length < desiredChars) {
                append(chunk)
            }
            if (length > desiredChars) {
                setLength(desiredChars)
            }
        }
        
        // Устанавливаем текст padding в поле ввода и отправляем
        _state.update { it.copy(currentMessage = paddingText) }
        sendMessage()
    }
    
    private fun sendMessage() {
        val currentState = _state.value
        if (currentState.currentMessage.isBlank()) return
        
        val userMessage = currentState.currentMessage
        
        // Очищаем поле ввода и предыдущий анализ, триггерим скролл
        _state.update { 
            it.copy(
                currentMessage = "",
                isLoading = true,
                similarityAnalysis = null,
                scrollTrigger = System.currentTimeMillis()  // Триггер для скролла
            )
        }
        
        viewModelScope.launch {
            try {
                // Добавляем сообщение пользователя (токены обновим после получения ответа)
                val newUserMsg = ChatMessage(
                    text = userMessage,
                    isUser = true,
                    responseMode = currentState.responseMode
                )
                
                // Сохраняем в БД и получаем ID (messages обновятся автоматически через Flow)
                val userMessageId = repository.saveMessage(newUserMsg)
                
                // Формируем историю для API
                val apiMessageHistory = if (currentState.responseMode == ResponseMode.TEMPERATURE_COMPARISON) {
                    emptyList()
                } else {
                    currentState.messages.map { chatMsg ->
                        Message(
                            role = if (chatMsg.isUser) "user" else "assistant",
                            text = chatMsg.text
                        )
                    }
                }
                
                // Отправляем с полной историей
                val result = if (currentState.responseMode == ResponseMode.TEMPERATURE_COMPARISON) {
                    gptClient.sendMessageWithTemperatureComparison(
                        userMessage = userMessage,
                        messageHistory = apiMessageHistory
                    )
                } else {
                    gptClient.sendMessage(
                        userMessage = userMessage,
                        messageHistory = apiMessageHistory,
                        responseMode = currentState.responseMode
                    )
                }
                
                when (result) {
                    is ApiResult.Success -> {
                        handleSuccessResponse(result.data, userMessageId)
                    }
                    is ApiResult.Error -> {
                        // Ошибка
                        val errorMsg = ChatMessage(
                            text = result.message,
                            isUser = false,
                            responseMode = ResponseMode.DEFAULT
                        )
                        repository.saveMessage(errorMsg)
                    }
                }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
    
    private suspend fun handleSuccessResponse(response: MessageResponse, userMessageId: Long = 0) {
        when (response) {
            is MessageResponse.StandardResponse -> {
                // Обновляем сообщение пользователя с токенами
                if (userMessageId > 0 && response.inputTextTokens != null) {
                    val userMsg = repository.getMessageById(userMessageId)
                    if (userMsg != null) {
                        repository.updateMessage(
                            userMsg.copy(tokensCount = response.inputTextTokens)
                        )
                    }
                }
                
                // Сохраняем ответ ассистента с токенами
                val assistantMsg = ChatMessage(
                    text = response.text,
                    isUser = false,
                    responseMode = ResponseMode.DEFAULT,
                    tokensCount = response.completionTokens
                )
                repository.saveMessage(assistantMsg)
            }
            
            is MessageResponse.FixedResponse -> {
                // Ответ с результатами поиска или задачами
                val assistantMsg = ChatMessage(
                    text = "Получено результатов: ${response.results.size}",
                    isUser = false,
                    responseMode = _state.value.responseMode,
                    rawResponse = response.rawText
                )
                val messageId = repository.saveMessage(assistantMsg)
                
                // Сохраняем результаты поиска
                repository.saveSearchResults(messageId, response.results)
                
                // Автоматически переходим на экран результатов
                _commandFlow.emit(ChatScreenCommand.NavigateToSearchResults(messageId))
            }
            
            is MessageResponse.TemperatureComparisonResponse -> {
                // Ответ с результатами сравнения температур
                val assistantMsg = ChatMessage(
                    text = "Получено ${response.results.size} ответов с разными температурами",
                    isUser = false,
                    responseMode = ResponseMode.TEMPERATURE_COMPARISON,
                    temperatureResults = response.results
                )
                repository.saveMessage(assistantMsg)
                
                // Добавляем результаты в накопительные списки
                val newTemp0 = _state.value.temperature0Responses.toMutableList()
                val newTemp05 = _state.value.temperature05Responses.toMutableList()
                val newTemp1 = _state.value.temperature1Responses.toMutableList()
                
                response.results.forEach { result ->
                    when (result.temperature) {
                        0.0 -> newTemp0.add(result.text)
                        0.5 -> newTemp05.add(result.text)
                        1.0 -> newTemp1.add(result.text)
                    }
                }
                
                _state.update { 
                    it.copy(
                        temperature0Responses = newTemp0,
                        temperature05Responses = newTemp05,
                        temperature1Responses = newTemp1
                    )
                }
                
                // Отправляем запрос на анализ ТОЛЬКО если есть хотя бы 2 ответа для сравнения
                if (newTemp0.size >= 2) {
                    analyzeTemperatureResults(newTemp0, newTemp05, newTemp1)
                }
            }
        }
    }
    
    private suspend fun analyzeTemperatureResults(
        temp0Responses: List<String>,
        temp05Responses: List<String>,
        temp1Responses: List<String>
    ) {
        println("📊 Отправляем запрос на анализ совпадений...")
        
        val analysisPrompt = buildString {
            appendLine("Ты — анализатор стабильности языковой модели.")
            appendLine()
            appendLine("На вход тебе подаются тексты, сгенерированные одним и тем же запросом,")
            appendLine("но при разных значениях температуры (например: 0.0, 0.5, 1.0)")
            appendLine("и за несколько итераций (несколько запусков одного и того же вопроса).")
            appendLine()
            appendLine("Твоя задача:")
            appendLine("1. Для каждой температуры рассматривать все её ответы как отдельную группу.")
            appendLine("2. Внутри каждой группы сравни каждый ответ со всеми другими.")
            appendLine("3. Для каждой пары ответов выполни посимвольное сравнение.")
            appendLine("4. Усредни полученные проценты по всем парам внутри группы.")
            appendLine()
            appendLine("=== ДАННЫЕ ===")
            appendLine("{")
            
            append("  \"temperature_0.0\": [")
            temp0Responses.forEachIndexed { index, text ->
                if (index > 0) append(", ")
                append("\"${text.replace("\"", "\\\"")}\"")
            }
            appendLine("],")
            
            append("  \"temperature_0.5\": [")
            temp05Responses.forEachIndexed { index, text ->
                if (index > 0) append(", ")
                append("\"${text.replace("\"", "\\\"")}\"")
            }
            appendLine("],")
            
            append("  \"temperature_1.0\": [")
            temp1Responses.forEachIndexed { index, text ->
                if (index > 0) append(", ")
                append("\"${text.replace("\"", "\\\"")}\"")
            }
            appendLine("]")
            appendLine("}")
            appendLine()
            appendLine("Формат вывода:")
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
                    _state.update { it.copy(similarityAnalysis = analysisResponse.text) }
                    println("✅ Анализ получен: ${analysisResponse.text}")
                }
            }
            is ApiResult.Error -> {
                _state.update { 
                    it.copy(similarityAnalysis = "Ошибка анализа: ${analysisResult.message}")
                }
                println("❌ Ошибка анализа: ${analysisResult.message}")
            }
        }
    }
}

