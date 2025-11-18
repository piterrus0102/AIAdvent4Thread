package ru.piterrus.aiadvent4thread.presentation.serverchat

sealed class ServerChatScreenIntent {
    data class InputTextChanged(val text: String) : ServerChatScreenIntent()
    object SendMessageClicked : ServerChatScreenIntent()
    object BackClicked : ServerChatScreenIntent()
    object ScrolledToBottom : ServerChatScreenIntent()
    object CheckConnection : ServerChatScreenIntent()
}

