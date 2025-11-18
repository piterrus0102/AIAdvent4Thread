package ru.piterrus.aiadvent4thread.presentation.serverchat

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
}

