package ru.piterrus.aiadvent4thread.presentation.serverchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.piterrus.aiadvent4thread.data.client.ChatMessage
import ru.piterrus.aiadvent4thread.data.client.LocalServerClient

class ServerChatScreenViewModel(
    private val serverClient: LocalServerClient
) : ViewModel() {

    private val _state = MutableStateFlow(ServerChatScreenState())
    val state: StateFlow<ServerChatScreenState> = _state.asStateFlow()

    private val _commandFlow = MutableSharedFlow<ServerChatScreenCommand>(extraBufferCapacity = 1)
    val commandFlow: SharedFlow<ServerChatScreenCommand> = _commandFlow.asSharedFlow()

    init {
        checkServerConnection()
        // Автоматически загружаем историю при открытии экрана
        viewModelScope.launch {
            delay(500) // Небольшая задержка, чтоб проверка подключения успела
            if (_state.value.isConnected) {
                loadMessagesFromServer()
            }
        }
    }

    fun intentToAction(intent: ServerChatScreenIntent) {
        when (intent) {
            is ServerChatScreenIntent.InputTextChanged -> {
                _state.update { it.copy(inputText = intent.text) }
            }

            is ServerChatScreenIntent.SendMessageClicked -> {
                sendMessage()
            }

            is ServerChatScreenIntent.BackClicked -> {
                viewModelScope.launch {
                    _commandFlow.emit(ServerChatScreenCommand.NavigateBack)
                }
            }

            is ServerChatScreenIntent.ScrolledToBottom -> {
                _state.update { it.copy(shouldScrollToBottom = false) }
            }

            is ServerChatScreenIntent.CheckConnection -> {
                checkServerConnection()
            }

            is ServerChatScreenIntent.GitHubToggleChanged -> {
                handleGitHubToggle(intent.enabled)
            }

            is ServerChatScreenIntent.GitHubAuthDialogDismissed -> {
                _state.update { 
                    it.copy(
                        showGitHubAuthDialog = false,
                        useGitHubMCP = false // Отключаем если закрыли без подтверждения
                    ) 
                }
            }

            is ServerChatScreenIntent.GitHubTokenChanged -> {
                _state.update { it.copy(githubToken = intent.token) }
            }

            is ServerChatScreenIntent.GitHubAuthConfirmed -> {
                confirmGitHubAuth()
            }
            is ServerChatScreenIntent.ClearChatClicked -> {
                clearChat()
            }
            is ServerChatScreenIntent.CopyMessageToClipboard -> {
                showSnackbar("Текст скопирован")
            }
        }
    }
    
    private fun showSnackbar(message: String) {
        _state.update { it.copy(snackbarMessage = message) }
        // Автоматически скрываем через 2 секунды
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _state.update { it.copy(snackbarMessage = null) }
        }
    }

    private fun checkServerConnection() {
        viewModelScope.launch {
            println("[ServerChatVM] Проверка подключения к серверу...")
            val result = serverClient.healthCheck()
            val isConnected = result.isSuccess

            _state.update { 
                it.copy(
                    isConnected = isConnected,
                    errorMessage = if (!isConnected) "Сервер недоступен. Убедитесь, что localserver запущен." else null
                )
            }

            println("[ServerChatVM] Статус подключения: $isConnected")
        }
    }

    private fun sendMessage() {
        val currentState = _state.value
        val messageText = currentState.inputText.trim()

        if (messageText.isEmpty()) {
            return
        }

        if (!currentState.isConnected) {
            _state.update {
                it.copy(errorMessage = "Невозможно отправить сообщение: сервер недоступен")
            }
            return
        }

        viewModelScope.launch {
            try {
                // Добавляем сообщение пользователя
                val userMessage = ServerChatMessage(
                    text = messageText,
                    isUser = true
                )

                _state.update {
                    it.copy(
                        messages = it.messages + userMessage,
                        inputText = "",
                        isLoading = true,
                        errorMessage = null,
                        shouldScrollToBottom = true
                    )
                }

                // Формируем историю для отправки на сервер
                val history = _state.value.messages
                    .dropLast(1) // Убираем последнее сообщение (только что добавленное)
                    .map { msg ->
                        ChatMessage(
                            role = if (msg.isUser) "user" else "assistant",
                            text = msg.text
                        )
                    }

                println("[ServerChatVM] Отправка сообщения на сервер...")
                println("[ServerChatVM] История: ${history.size} сообщений")

                // Отправляем запрос на сервер (используется YandexGPT)
                val result = serverClient.sendMessage(
                    message = messageText,
                    history = history
                )

                result.fold(
                    onSuccess = { response ->
                        if (response.success && response.message != null) {
                            println("[ServerChatVM] ✅ Получен ответ от сервера")
                            if (response.toolUsed != null) {
                                println("[ServerChatVM] 🔧 Использован инструмент: ${response.toolUsed}")
                            }

                            // Добавляем ответ ассистента
                            val assistantMessage = ServerChatMessage(
                                text = response.message,
                                isUser = false,
                                toolUsed = response.toolUsed,
                                toolResult = response.toolResult
                            )

                            _state.update {
                                it.copy(
                                    messages = it.messages + assistantMessage,
                                    isLoading = false,
                                    shouldScrollToBottom = true
                                )
                            }

                            // Синхронизируем сообщения с сервером в БД
                            syncMessagesToServer()
                        } else {
                            val errorMsg = response.error ?: "Неизвестная ошибка сервера"
                            println("[ServerChatVM] ❌ Ошибка: $errorMsg")
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = errorMsg
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        println("[ServerChatVM] ❌ Ошибка при отправке: ${error.message}")
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Ошибка связи с сервером: ${error.message}"
                            )
                        }
                    }
                )

            } catch (e: Exception) {
                println("[ServerChatVM] ❌ Исключение: ${e.message}")
                e.printStackTrace()
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Произошла ошибка: ${e.message}"
                    )
                }
            }
        }
    }

    private fun handleGitHubToggle(enabled: Boolean) {
        if (enabled) {
            // Показываем диалог авторизации
            _state.update { 
                it.copy(showGitHubAuthDialog = true) 
            }
        } else {
            // Отключаем GitHub MCP
            viewModelScope.launch {
                val result = serverClient.setMCPMode(useGitHub = false)
                result.fold(
                    onSuccess = {
                        println("[ServerChatVM] ✅ Переключено на локальный MCP")
                        _state.update { 
                            it.copy(
                                useGitHubMCP = false,
                                errorMessage = null
                            ) 
                        }
                    },
                    onFailure = { error ->
                        println("[ServerChatVM] ❌ Ошибка переключения MCP: ${error.message}")
                        _state.update { 
                            it.copy(
                                errorMessage = "Ошибка переключения режима: ${error.message}"
                            ) 
                        }
                    }
                )
            }
        }
    }

    private fun confirmGitHubAuth() {
        val currentState = _state.value
        val token = currentState.githubToken.trim()

        if (token.isEmpty()) {
            _state.update { 
                it.copy(errorMessage = "Введите GitHub Token") 
            }
            return
        }

        viewModelScope.launch {
            _state.update { 
                it.copy(
                    showGitHubAuthDialog = false,
                    isLoading = true
                ) 
            }

            println("[ServerChatVM] Подключение к GitHub MCP...")
            println("[ServerChatVM] Token: ${token.take(10)}...")
            
            val result = serverClient.setMCPMode(
                useGitHub = true,
                githubToken = token
            )

            result.fold(
                onSuccess = { response ->
                    println("[ServerChatVM] ✅ Переключено на GitHub MCP: ${response.mode}")
                    _state.update { 
                        it.copy(
                            useGitHubMCP = true,
                            isLoading = false,
                            errorMessage = null
                        ) 
                    }
                },
                onFailure = { error ->
                    println("[ServerChatVM] ❌ Ошибка подключения к GitHub MCP: ${error.message}")
                    _state.update { 
                        it.copy(
                            useGitHubMCP = false,
                            isLoading = false,
                            errorMessage = "Ошибка подключения к GitHub: ${error.message}"
                        ) 
                    }
                }
            )
        }
    }

    private fun syncMessagesToServer() {
        viewModelScope.launch {
            try {
                val messages = _state.value.messages.map { msg ->
                    ru.piterrus.aiadvent4thread.data.client.SyncMessage(
                        messageId = "msg_${msg.timestamp}_${msg.text.hashCode()}",
                        text = msg.text,
                        isUser = msg.isUser,
                        timestamp = msg.timestamp,
                        modelName = null,
                        toolUsed = msg.toolUsed,
                        toolResult = msg.toolResult,
                        screenType = "server_chat"
                    )
                }

                println("[ServerChatVM] Синхронизация ${messages.size} сообщений с сервером...")
                val result = serverClient.syncMessages(messages)
                
                result.fold(
                    onSuccess = { response ->
                        println("[ServerChatVM] ✅ Синхронизировано: ${response.synced}")
                    },
                    onFailure = { error ->
                        println("[ServerChatVM] ⚠️ Ошибка синхронизации: ${error.message}")
                        // Не показываем ошибку пользователю, это фоновая операция
                    }
                )
            } catch (e: Exception) {
                println("[ServerChatVM] ⚠️ Исключение при синхронизации: ${e.message}")
            }
        }
    }

    private fun loadMessagesFromServer() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }
                
                println("[ServerChatVM] Загрузка сообщений с сервера...")
                val result = serverClient.getMessages(limit = 100, screenType = "server_chat")
                
                result.fold(
                    onSuccess = { response ->
                        if (response.success && response.messages != null) {
                            println("[ServerChatVM] ✅ Загружено сообщений: ${response.count}")
                            
                            // Преобразуем в ServerChatMessage
                            val loadedMessages = response.messages.map { msg ->
                                ServerChatMessage(
                                    text = msg.text,
                                    isUser = msg.isUser,
                                    timestamp = msg.timestamp,
                                    toolUsed = msg.toolUsed,
                                    toolResult = msg.toolResult
                                )
                            }.sortedBy { it.timestamp } // Сортируем по времени
                            
                            _state.update { 
                                it.copy(
                                    messages = loadedMessages,
                                    isLoading = false,
                                    errorMessage = null,
                                    shouldScrollToBottom = true
                                ) 
                            }
                        } else {
                            println("[ServerChatVM] ❌ Ошибка загрузки: ${response.error}")
                            _state.update { 
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Не удалось загрузить сообщения"
                                ) 
                            }
                        }
                    },
                    onFailure = { error ->
                        println("[ServerChatVM] ❌ Ошибка загрузки: ${error.message}")
                        _state.update { 
                            it.copy(
                                isLoading = false,
                                errorMessage = "Ошибка загрузки истории: ${error.message}"
                            ) 
                        }
                    }
                )
            } catch (e: Exception) {
                println("[ServerChatVM] ❌ Исключение при загрузке: ${e.message}")
                _state.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Ошибка: ${e.message}"
                    ) 
                }
            }
        }
    }

    private fun clearChat() {
        viewModelScope.launch {
            try {
                println("[ServerChatVM] Очистка чата...")
                
                // Очищаем локально
                _state.update { 
                    it.copy(
                        messages = emptyList(),
                        shouldScrollToBottom = false
                    ) 
                }
                
                // Очищаем на сервере
                val result = serverClient.clearMessages(screenType = "server_chat")
                
                result.fold(
                    onSuccess = { response ->
                        if (response.success) {
                            println("[ServerChatVM] ✅ Очищено сообщений: ${response.deleted}")
                        } else {
                            println("[ServerChatVM] ❌ Ошибка очистки: ${response.error}")
                        }
                    },
                    onFailure = { error ->
                        println("[ServerChatVM] ❌ Ошибка очистки на сервере: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                println("[ServerChatVM] ❌ Исключение при очистке: ${e.message}")
            }
        }
    }
}

