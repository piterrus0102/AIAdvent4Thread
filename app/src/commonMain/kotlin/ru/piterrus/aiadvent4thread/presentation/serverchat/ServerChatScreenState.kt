package ru.piterrus.aiadvent4thread.presentation.serverchat

data class ServerChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val toolUsed: String? = null,
    val toolResult: String? = null
)

data class ServerChatScreenState(
    val messages: List<ServerChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isConnected: Boolean = false,
    val shouldScrollToBottom: Boolean = false
)

