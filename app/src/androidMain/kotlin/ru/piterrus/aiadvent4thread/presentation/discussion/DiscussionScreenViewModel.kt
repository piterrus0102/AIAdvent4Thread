package ru.piterrus.aiadvent4thread.presentation.discussion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
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

class DiscussionScreenViewModel(
    private val gptClient: YandexGPTClient
) : ViewModel() {
    private val _state = MutableStateFlow(DiscussionScreenState())
    val state: StateFlow<DiscussionScreenState> = _state.asStateFlow()
    
    private val _commandFlow = MutableSharedFlow<DiscussionScreenCommand>(extraBufferCapacity = 1)
    val commandFlow: SharedFlow<DiscussionScreenCommand> = _commandFlow.asSharedFlow()
    
    fun intentToAction(intent: DiscussionScreenIntent) {
        when (intent) {
            is DiscussionScreenIntent.TopicChanged -> {
                _state.update { it.copy(topic = intent.topic) }
            }
            
            is DiscussionScreenIntent.StartDiscussion -> {
                startDiscussion()
            }
            
            is DiscussionScreenIntent.ResetDiscussion -> {
                _state.update { 
                    it.copy(
                        roles = emptyList(),
                        summary = "",
                        errorMessage = null
                    )
                }
            }
            
            is DiscussionScreenIntent.ExpertClicked -> {
                viewModelScope.launch {
                    _commandFlow.emit(DiscussionScreenCommand.NavigateToExpertDetail(intent.expert, intent.expertNumber))
                }
            }
            
            is DiscussionScreenIntent.BackClicked -> {
                viewModelScope.launch {
                    _commandFlow.emit(DiscussionScreenCommand.NavigateBack)
                }
            }
        }
    }
    
    private fun startDiscussion() {
        val currentState = _state.value
        if (currentState.topic.isBlank()) return
        
        _state.update { 
            it.copy(
                isLoadingRoles = true,
                errorMessage = null
            )
        }
        
        viewModelScope.launch {
            try {
                // Шаг 1: Получаем роли от координатора
                val result = gptClient.sendMessage(
                    userMessage = currentState.topic,
                    messageHistory = listOf(
                        Message(role = "system", text = Prompts.discussPrompt)
                    ),
                    responseMode = ResponseMode.DEFAULT
                )
                
                when (result) {
                    is ApiResult.Success -> {
                        when (val response = result.data) {
                            is MessageResponse.StandardResponse -> {
                                val parsedRoles = DiscussionParser.parseRoles(response.text)
                                if (parsedRoles.isNotEmpty()) {
                                    _state.update { it.copy(roles = parsedRoles) }
                                    
                                    // Шаг 2: Параллельно запрашиваем ответы от каждого эксперта
                                    val updatedRoles = parsedRoles.mapIndexed { _, role ->
                                        viewModelScope.async {
                                            val expertResult = gptClient.sendMessage(
                                                userMessage = currentState.topic,
                                                messageHistory = listOf(
                                                    Message(
                                                        role = "system",
                                                        text = Prompts.expertPrompt(role.name, role.description, currentState.topic)
                                                    )
                                                ),
                                                responseMode = ResponseMode.DEFAULT
                                            )
                                            
                                            when (expertResult) {
                                                is ApiResult.Success -> {
                                                    when (val expertResponse = expertResult.data) {
                                                        is MessageResponse.StandardResponse -> {
                                                            role.copy(answer = expertResponse.text)
                                                        }
                                                        else -> role.copy(answer = "Ошибка получения ответа")
                                                    }
                                                }
                                                is ApiResult.Error -> {
                                                    role.copy(answer = expertResult.message)
                                                }
                                            }
                                        }
                                    }
                                    
                                    val finalRoles = updatedRoles.map { it.await() }
                                    _state.update { it.copy(roles = finalRoles) }
                                    
                                    // Шаг 3: Получаем суммаризацию
                                    _state.update { it.copy(isLoadingSummary = true) }
                                    
                                    try {
                                        val expertsText = finalRoles.mapIndexed { index, role ->
                                            "=== ЭКСПЕРТ ${index + 1}: ${role.name} ===\n${role.answer}"
                                        }.joinToString("\n\n")
                                        
                                        val summaryResult = gptClient.sendMessage(
                                            userMessage = "ТЕМА: ${currentState.topic}\n\n$expertsText",
                                            messageHistory = listOf(
                                                Message(role = "system", text = Prompts.summarizePrompt)
                                            ),
                                            responseMode = ResponseMode.DEFAULT
                                        )
                                        
                                        when (summaryResult) {
                                            is ApiResult.Success -> {
                                                when (val summaryResponse = summaryResult.data) {
                                                    is MessageResponse.StandardResponse -> {
                                                        _state.update { it.copy(summary = summaryResponse.text) }
                                                    }
                                                    else -> {
                                                        _state.update { 
                                                            it.copy(summary = "Ошибка: неожиданный тип ответа")
                                                        }
                                                    }
                                                }
                                            }
                                            is ApiResult.Error -> {
                                                _state.update { 
                                                    it.copy(summary = "Ошибка при суммаризации: ${summaryResult.message}")
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        _state.update { it.copy(summary = "Ошибка: ${e.message}") }
                                    } finally {
                                        _state.update { it.copy(isLoadingSummary = false) }
                                    }
                                } else {
                                    _state.update { 
                                        it.copy(errorMessage = "Не удалось распознать роли. Попробуйте другую тему.")
                                    }
                                }
                            }
                            else -> {
                                _state.update { it.copy(errorMessage = "Неожиданный тип ответа") }
                            }
                        }
                    }
                    is ApiResult.Error -> {
                        _state.update { it.copy(errorMessage = result.message) }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Ошибка: ${e.message}") }
            } finally {
                _state.update { it.copy(isLoadingRoles = false) }
            }
        }
    }
}

