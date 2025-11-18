package ru.piterrus.aiadvent4thread.presentation.huggingface

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
import ru.piterrus.aiadvent4thread.data.client.HuggingFaceClient
import ru.piterrus.aiadvent4thread.data.client.LocalServerClient
import ru.piterrus.aiadvent4thread.data.model.*

class HuggingFaceScreenViewModel(
    private val hfClient: HuggingFaceClient,
    private val serverClient: LocalServerClient
) : ViewModel() {
    private val _state = MutableStateFlow(HuggingFaceScreenState())
    val state: StateFlow<HuggingFaceScreenState> = _state.asStateFlow()
    
    private val _commandFlow = MutableSharedFlow<HuggingFaceScreenCommand>(extraBufferCapacity = 1)
    val commandFlow: SharedFlow<HuggingFaceScreenCommand> = _commandFlow.asSharedFlow()
    
    init {
        loadMessageCounts()
    }
    
    private fun loadMessageCounts() {
        viewModelScope.launch {
            println("[HuggingFaceVM] Загрузка счетчиков с сервера...")
            val result = serverClient.getMessageCount()
            
            result.fold(
                onSuccess = { response ->
                    if (response.success && response.models != null) {
                        println("[HuggingFaceVM] ✅ Счетчики загружены: ${response.models}")
                        _state.update { 
                            it.copy(modelCounts = response.models)
                        }
                    }
                },
                onFailure = { error ->
                    println("[HuggingFaceVM] ⚠️ Не удалось загрузить счетчики: ${error.message}")
                    // Оставляем нулевые значения по умолчанию
                }
            )
        }
    }
    
    private fun updateMessageCount(modelName: String, count: Int) {
        viewModelScope.launch {
            println("[HuggingFaceVM] Обновление счетчика для $modelName: $count")
            val result = serverClient.updateMessageCount(modelName, count)

            result.fold(
                onSuccess = {
                    println("[HuggingFaceVM] ✅ Счетчик обновлен на сервере для $modelName")
                    _state.update { 
                        val updatedCounts = it.modelCounts.toMutableMap()
                        updatedCounts[modelName] = count
                        it.copy(modelCounts = updatedCounts)
                    }
                },
                onFailure = { error ->
                    println("[HuggingFaceVM] ⚠️ Не удалось обновить счетчик: ${error.message}")
                    // Не блокируем отправку сообщения из-за ошибки счетчика
                }
            )
        }
    }
    
    fun intentToAction(intent: HuggingFaceScreenIntent) {
        when (intent) {
            is HuggingFaceScreenIntent.TabSelected -> {
                _state.update { it.copy(selectedTabIndex = intent.index) }
            }
            
            is HuggingFaceScreenIntent.SthenoInputChanged -> {
                _state.update { it.copy(sthenoInput = intent.input) }
            }
            
            is HuggingFaceScreenIntent.MiniMaxInputChanged -> {
                _state.update { it.copy(miniMaxInput = intent.input) }
            }
            
            is HuggingFaceScreenIntent.Qwen2InputChanged -> {
                _state.update { it.copy(qwen2Input = intent.input) }
            }
            
            is HuggingFaceScreenIntent.SendSthenoMessage -> {
                sendSthenoMessage()
            }
            
            is HuggingFaceScreenIntent.SendMiniMaxMessage -> {
                sendMiniMaxMessage()
            }
            
            is HuggingFaceScreenIntent.SendQwen2Message -> {
                sendQwen2Message()
            }
            
            is HuggingFaceScreenIntent.ClearSthenoHistory -> {
                _state.update { it.copy(sthenoMessages = emptyList()) }
                // Обнуляем счетчик на сервере
                updateMessageCount("L3-8B-Stheno", 0)
            }
            
            is HuggingFaceScreenIntent.ClearMiniMaxHistory -> {
                _state.update { it.copy(miniMaxMessages = emptyList()) }
                // Обнуляем счетчик на сервере
                updateMessageCount("MiniMax-M2", 0)
            }
            
            is HuggingFaceScreenIntent.ClearQwen2History -> {
                _state.update { it.copy(qwen2Messages = emptyList()) }
                // Обнуляем счетчик на сервере
                updateMessageCount("Qwen2.5-7B-Instruct", 0)
            }
            
            is HuggingFaceScreenIntent.Qwen2ThinkingModeChanged -> {
                _state.update { it.copy(qwen2ThinkingMode = intent.enabled) }
            }
            
            is HuggingFaceScreenIntent.BackClicked -> {
                viewModelScope.launch {
                    _commandFlow.emit(HuggingFaceScreenCommand.NavigateBack)
                }
            }
        }
    }
    
    private fun sendSthenoMessage() {
        val currentState = _state.value
        if (currentState.sthenoInput.isBlank()) return
        
        val userPrompt = currentState.sthenoInput
        val modelName = "L3-8B-Stheno"
        
        // Добавляем сообщение пользователя
        _state.update { 
            it.copy(
                sthenoMessages = it.sthenoMessages + HFChatMessage(
                    text = userPrompt,
                    isUser = true,
                    model = HFModel.STHENO
                ),
                sthenoInput = "",
                isSthenoLoading = true
            )
        }
        
        // Увеличиваем счетчик для сообщения пользователя
        val userMessageCount = (currentState.modelCounts[modelName] ?: 0) + 1
        println("[HuggingFaceVM] Увеличение счетчика (сообщение пользователя) для $modelName: $userMessageCount")
        updateMessageCount(modelName, userMessageCount)
        
        viewModelScope.launch {
            try {
                val result = hfClient.callStheno(userPrompt)
                
                when (result) {
                    is HuggingFaceResult.Success -> {
                        _state.update { 
                            it.copy(
                                sthenoMessages = it.sthenoMessages + HFChatMessage(
                                    text = result.text,
                                    isUser = false,
                                    model = HFModel.STHENO,
                                    timeTaken = result.timeTaken,
                                    tokensUsed = result.tokensUsed
                                )
                            )
                        }
                        
                        // Увеличиваем счетчик для ответа ассистента
                        val assistantMessageCount = (_state.value.modelCounts[modelName] ?: 0) + 1
                        println("[HuggingFaceVM] Увеличение счетчика (ответ ассистента) для $modelName: $assistantMessageCount")
                        updateMessageCount(modelName, assistantMessageCount)
                    }
                    is HuggingFaceResult.Error -> {
                        _state.update { 
                            it.copy(
                                sthenoMessages = it.sthenoMessages + HFChatMessage(
                                    text = result.message,
                                    isUser = false,
                                    model = HFModel.STHENO
                                )
                            )
                        }
                        
                        // Увеличиваем счетчик даже для сообщения об ошибке
                        val assistantMessageCount = (_state.value.modelCounts[modelName] ?: 0) + 1
                        println("[HuggingFaceVM] Увеличение счетчика (ошибка) для $modelName: $assistantMessageCount")
                        updateMessageCount(modelName, assistantMessageCount)
                    }
                }
            } finally {
                _state.update { it.copy(isSthenoLoading = false) }
            }
        }
    }
    
    private fun sendMiniMaxMessage() {
        val currentState = _state.value
        if (currentState.miniMaxInput.isBlank()) return
        
        val userPrompt = currentState.miniMaxInput
        val modelName = "MiniMax-M2"
        
        // Добавляем сообщение пользователя
        _state.update { 
            it.copy(
                miniMaxMessages = it.miniMaxMessages + HFChatMessage(
                    text = userPrompt,
                    isUser = true,
                    model = HFModel.MINIMAX
                ),
                miniMaxInput = "",
                isMiniMaxLoading = true
            )
        }
        
        // Увеличиваем счетчик для сообщения пользователя
        val userMessageCount = (currentState.modelCounts[modelName] ?: 0) + 1
        println("[HuggingFaceVM] Увеличение счетчика (сообщение пользователя) для $modelName: $userMessageCount")
        updateMessageCount(modelName, userMessageCount)
        
        viewModelScope.launch {
            try {
                val result = hfClient.callMiniMax(userPrompt)
                
                when (result) {
                    is HuggingFaceResult.Success -> {
                        _state.update { 
                            it.copy(
                                miniMaxMessages = it.miniMaxMessages + HFChatMessage(
                                    text = result.text,
                                    isUser = false,
                                    model = HFModel.MINIMAX,
                                    timeTaken = result.timeTaken,
                                    tokensUsed = result.tokensUsed
                                )
                            )
                        }
                        
                        // Увеличиваем счетчик для ответа ассистента
                        val assistantMessageCount = (_state.value.modelCounts[modelName] ?: 0) + 1
                        println("[HuggingFaceVM] Увеличение счетчика (ответ ассистента) для $modelName: $assistantMessageCount")
                        updateMessageCount(modelName, assistantMessageCount)
                    }
                    is HuggingFaceResult.Error -> {
                        _state.update { 
                            it.copy(
                                miniMaxMessages = it.miniMaxMessages + HFChatMessage(
                                    text = result.message,
                                    isUser = false,
                                    model = HFModel.MINIMAX
                                )
                            )
                        }
                        
                        // Увеличиваем счетчик даже для сообщения об ошибке
                        val assistantMessageCount = (_state.value.modelCounts[modelName] ?: 0) + 1
                        println("[HuggingFaceVM] Увеличение счетчика (ошибка) для $modelName: $assistantMessageCount")
                        updateMessageCount(modelName, assistantMessageCount)
                    }
                }
            } finally {
                _state.update { it.copy(isMiniMaxLoading = false) }
            }
        }
    }
    
    private fun sendQwen2Message() {
        val currentState = _state.value
        if (currentState.qwen2Input.isBlank()) return
        
        val userPrompt = currentState.qwen2Input
        val modelName = "Qwen2.5-7B-Instruct"
        
        // Добавляем сообщение пользователя
        _state.update { 
            it.copy(
                qwen2Messages = it.qwen2Messages + HFChatMessage(
                    text = userPrompt,
                    isUser = true,
                    model = HFModel.QWEN2
                ),
                qwen2Input = "",
                isQwen2Loading = true
            )
        }
        
        // Увеличиваем счетчик для сообщения пользователя
        val userMessageCount = (currentState.modelCounts[modelName] ?: 0) + 1
        println("[HuggingFaceVM] Увеличение счетчика (сообщение пользователя) для $modelName: $userMessageCount")
        updateMessageCount(modelName, userMessageCount)
        
        viewModelScope.launch {
            try {
                val result = hfClient.callQwen2(userPrompt, currentState.qwen2ThinkingMode)
                
                when (result) {
                    is HuggingFaceResult.Success -> {
                        _state.update { 
                            it.copy(
                                qwen2Messages = it.qwen2Messages + HFChatMessage(
                                    text = result.text,
                                    isUser = false,
                                    model = HFModel.QWEN2,
                                    timeTaken = result.timeTaken,
                                    tokensUsed = result.tokensUsed,
                                    thinkingContent = result.thinkingContent
                                )
                            )
                        }
                        
                        // Увеличиваем счетчик для ответа ассистента
                        val assistantMessageCount = (_state.value.modelCounts[modelName] ?: 0) + 1
                        println("[HuggingFaceVM] Увеличение счетчика (ответ ассистента) для $modelName: $assistantMessageCount")
                        updateMessageCount(modelName, assistantMessageCount)
                    }
                    is HuggingFaceResult.Error -> {
                        _state.update { 
                            it.copy(
                                qwen2Messages = it.qwen2Messages + HFChatMessage(
                                    text = result.message,
                                    isUser = false,
                                    model = HFModel.QWEN2
                                )
                            )
                        }
                        
                        // Увеличиваем счетчик даже для сообщения об ошибке
                        val assistantMessageCount = (_state.value.modelCounts[modelName] ?: 0) + 1
                        println("[HuggingFaceVM] Увеличение счетчика (ошибка) для $modelName: $assistantMessageCount")
                        updateMessageCount(modelName, assistantMessageCount)
                    }
                }
            } finally {
                _state.update { it.copy(isQwen2Loading = false) }
            }
        }
    }
}

