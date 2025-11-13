package ru.piterrus.aiadvent4thread.presentation.chat

import ru.piterrus.aiadvent4thread.data.model.ChatMessage
import ru.piterrus.aiadvent4thread.data.model.ResponseMode

sealed interface ChatScreenIntent {
    data class MessageChanged(val message: String) : ChatScreenIntent
    object SendMessage : ChatScreenIntent
    data class SendContextPadding(val tokens: Int) : ChatScreenIntent
    object ClearHistory : ChatScreenIntent
    object CompressHistory : ChatScreenIntent  // Новый Intent для сжатия истории
    data class ResponseModeToggle(val mode: ResponseMode) : ChatScreenIntent
    data class MessageClicked(val message: ChatMessage) : ChatScreenIntent
    data class TemperatureResultClicked(val message: ChatMessage, val index: Int) : ChatScreenIntent
    data class CopyMessageText(val text: String) : ChatScreenIntent
    object BackToStart : ChatScreenIntent
    object ScrolledToBottom : ChatScreenIntent
}

