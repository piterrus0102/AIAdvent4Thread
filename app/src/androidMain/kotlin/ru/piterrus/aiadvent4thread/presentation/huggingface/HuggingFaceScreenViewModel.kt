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
import ru.piterrus.aiadvent4thread.data.model.*

class HuggingFaceScreenViewModel(
    private val hfClient: HuggingFaceClient
) : ViewModel() {
    private val _state = MutableStateFlow(HuggingFaceScreenState())
    val state: StateFlow<HuggingFaceScreenState> = _state.asStateFlow()
    
    private val _commandFlow = MutableSharedFlow<HuggingFaceScreenCommand>(extraBufferCapacity = 1)
    val commandFlow: SharedFlow<HuggingFaceScreenCommand> = _commandFlow.asSharedFlow()
    
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
            }
            
            is HuggingFaceScreenIntent.ClearMiniMaxHistory -> {
                _state.update { it.copy(miniMaxMessages = emptyList()) }
            }
            
            is HuggingFaceScreenIntent.ClearQwen2History -> {
                _state.update { it.copy(qwen2Messages = emptyList()) }
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
                    }
                }
            } finally {
                _state.update { it.copy(isQwen2Loading = false) }
            }
        }
    }
}

