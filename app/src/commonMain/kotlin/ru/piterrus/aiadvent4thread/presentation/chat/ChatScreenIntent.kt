package ru.piterrus.aiadvent4thread.presentation.chat

import ru.piterrus.aiadvent4thread.data.model.ChatMessage
import ru.piterrus.aiadvent4thread.data.model.ResponseMode

sealed interface ChatScreenIntent {
    data class MessageChanged(val message: String) : ChatScreenIntent
    object SendMessage : ChatScreenIntent
    object ClearHistory : ChatScreenIntent
    data class ResponseModeToggle(val mode: ResponseMode) : ChatScreenIntent
    data class MessageClicked(val message: ChatMessage) : ChatScreenIntent
    data class TemperatureResultClicked(val message: ChatMessage, val index: Int) : ChatScreenIntent
    object BackToStart : ChatScreenIntent
    object ScrolledToBottom : ChatScreenIntent
}

