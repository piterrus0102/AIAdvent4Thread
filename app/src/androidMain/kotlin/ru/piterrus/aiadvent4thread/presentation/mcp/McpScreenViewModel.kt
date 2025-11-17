package ru.piterrus.aiadvent4thread.presentation.mcp

import android.util.Log
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
import ru.piterrus.aiadvent4thread.data.client.McpClient

class McpScreenViewModel(
    private val mcpClient: McpClient
) : ViewModel() {
    private val _state = MutableStateFlow(McpScreenState())
    val state: StateFlow<McpScreenState> = _state.asStateFlow()
    
    private val _commandFlow = MutableSharedFlow<McpScreenCommand>(extraBufferCapacity = 1)
    val commandFlow: SharedFlow<McpScreenCommand> = _commandFlow.asSharedFlow()
    
    fun intentToAction(intent: McpScreenIntent) {
        when (intent) {
            is McpScreenIntent.ServerUrlChanged -> {
                _state.update { it.copy(serverUrl = intent.url, errorMessage = null) }
            }
            
            is McpScreenIntent.ConnectClicked -> {
                connectToMcp()
            }
            
            is McpScreenIntent.DisconnectClicked -> {
                disconnectFromMcp()
            }
            
            is McpScreenIntent.BackClicked -> {
                viewModelScope.launch {
                    _commandFlow.emit(McpScreenCommand.NavigateBack)
                }
            }
        }
    }
    
    private fun connectToMcp() {
        viewModelScope.launch {
            val currentUrl = _state.value.serverUrl
            
            _state.update { 
                it.copy(
                    isConnecting = true, 
                    errorMessage = null,
                    connectionStatus = "Подключение..."
                ) 
            }
            
            Log.d("McpViewModel", "Connecting to MCP proxy at: $currentUrl")
            
            val connectResult = mcpClient.connect(currentUrl)
            
            if (connectResult.isSuccess) {
                Log.d("McpViewModel", "Successfully connected to MCP")
                
                val toolsResult = mcpClient.getTools(currentUrl)
                
                if (toolsResult.isSuccess) {
                    val tools = toolsResult.getOrNull()
                    Log.d("McpViewModel", "Received ${tools?.tools?.size ?: 0} tools")
                    
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            isConnected = true,
                            connectionStatus = "Подключено",
                            tools = tools?.tools?.map { tool ->
                                McpTool(
                                    name = tool.name,
                                    description = tool.description ?: "",
                                    inputSchema = tool.inputSchema?.let { schema ->
                                        buildSchemaString(schema.type, schema.properties, schema.required)
                                    } ?: ""
                                )
                            } ?: emptyList(),
                            errorMessage = null
                        )
                    }
                } else {
                    Log.e("McpViewModel", "Failed to get tools")
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            isConnected = false,
                            connectionStatus = "Ошибка при получении инструментов",
                            errorMessage = "Не удалось получить список инструментов"
                        )
                    }
                }
            } else {
                Log.e("McpViewModel", "Failed to connect")
                _state.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = false,
                        connectionStatus = "Ошибка подключения",
                        errorMessage = "Не удалось подключиться к серверу"
                    )
                }
            }
        }
    }
    
    private fun disconnectFromMcp() {
        viewModelScope.launch {
            val currentUrl = _state.value.serverUrl
            Log.d("McpViewModel", "Disconnecting from MCP")
            
            val result = mcpClient.disconnect(currentUrl)
            
            _state.update {
                it.copy(
                    isConnected = false,
                    tools = emptyList(),
                    connectionStatus = "Отключено",
                    errorMessage = null
                )
            }
        }
    }
    
    private fun buildSchemaString(
        type: String?,
        properties: Map<String, ru.piterrus.aiadvent4thread.data.model.SchemaProperty>?,
        required: List<String>?
    ): String {
        val parts = mutableListOf<String>()
        
        if (type != null) {
            parts.add("type: $type")
        }
        
        if (!properties.isNullOrEmpty()) {
            val propsStr = properties.entries.joinToString(", ") { (key, prop) ->
                val isRequired = required?.contains(key) == true
                val requiredMarker = if (isRequired) "*" else ""
                "$key$requiredMarker: ${prop.type ?: "any"}"
            }
            parts.add("properties: {$propsStr}")
        }
        
        return parts.joinToString(", ")
    }
}
