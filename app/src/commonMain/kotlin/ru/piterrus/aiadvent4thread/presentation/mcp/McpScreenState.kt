package ru.piterrus.aiadvent4thread.presentation.mcp

data class McpScreenState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val serverUrl: String = "http://10.0.2.2:3000", // Локальный прокси для эмулятора Android
    val tools: List<McpTool> = emptyList(),
    val errorMessage: String? = null,
    val connectionStatus: String = "Не подключено"
)

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: String
)
