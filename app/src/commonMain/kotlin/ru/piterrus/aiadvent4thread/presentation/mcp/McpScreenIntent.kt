package ru.piterrus.aiadvent4thread.presentation.mcp

sealed interface McpScreenIntent {
    data class ServerUrlChanged(val url: String) : McpScreenIntent
    object ConnectClicked : McpScreenIntent
    object DisconnectClicked : McpScreenIntent
    object BackClicked : McpScreenIntent
}
