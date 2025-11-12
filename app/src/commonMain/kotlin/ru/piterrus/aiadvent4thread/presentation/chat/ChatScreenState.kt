package ru.piterrus.aiadvent4thread.presentation.chat

import ru.piterrus.aiadvent4thread.data.model.ChatMessage
import ru.piterrus.aiadvent4thread.data.model.ResponseMode

data class ChatScreenState(
    val messages: List<ChatMessage> = emptyList(),
    val currentMessage: String = "",
    val isLoading: Boolean = false,
    val responseMode: ResponseMode = ResponseMode.DEFAULT,
    val similarityAnalysis: String? = null,
    val shouldScrollToBottom: Boolean = false,
    val temperature0Responses: List<String> = emptyList(),
    val temperature05Responses: List<String> = emptyList(),
    val temperature1Responses: List<String> = emptyList(),
    val scrollTrigger: Long = 0L  // Триггер для принудительного скролла
)

