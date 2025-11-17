package ru.piterrus.aiadvent4thread.presentation.mcp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpScreen(
    state: McpScreenState,
    onIntent: (McpScreenIntent) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🔌 MCP Connection",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onIntent(McpScreenIntent.BackClicked) },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "←",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF6A0DAD)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF6A0DAD),
                            Color(0xFF8B3FA8),
                            Color(0xFFFF7F50)
                        )
                    )
                )
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Connection Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Настройки подключения",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6A0DAD)
                        )
                        
                        // Server URL Input
                        OutlinedTextField(
                            value = state.serverUrl,
                            onValueChange = { onIntent(McpScreenIntent.ServerUrlChanged(it)) },
                            label = { Text("URL прокси-сервера") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isConnected,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6A0DAD),
                                focusedLabelColor = Color(0xFF6A0DAD),
                            )
                        )
                        
                        // Status Text
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Статус: ${state.connectionStatus}",
                                fontSize = 14.sp,
                                color = when {
                                    state.isConnected -> Color(0xFF4CAF50)
                                    state.errorMessage != null -> Color(0xFFE91E63)
                                    else -> Color.Gray
                                },
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // Error Message
                        if (state.errorMessage != null) {
                            Surface(
                                color = Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.errorMessage,
                                    color = Color(0xFFE91E63),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        
                        // Connect/Disconnect Button
                        Button(
                            onClick = {
                                if (state.isConnected) {
                                    onIntent(McpScreenIntent.DisconnectClicked)
                                } else {
                                    onIntent(McpScreenIntent.ConnectClicked)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isConnecting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isConnected) Color(0xFFE91E63) else Color(0xFF6A0DAD)
                            )
                        ) {
                            if (state.isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Подключение...")
                            } else {
                                Text(
                                    text = if (state.isConnected) "Отключиться" else "Подключиться",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                // Architecture Info Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF8F9FF),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "ℹ️",
                                fontSize = 24.sp
                            )
                            Text(
                                text = "Архитектура подключения",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6A0DAD)
                            )
                        }
                        
                        // Connection flow
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "📱 Android",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF666666)
                                )
                                Text(
                                    text = "  ↓ HTTP/REST",
                                    fontSize = 11.sp,
                                    color = Color(0xFF999999),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                Text(
                                    text = "🖥️ Node.js Proxy (Express)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF666666)
                                )
                                Text(
                                    text = "  ↓ stdio (stdin/stdout)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF999999),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                Text(
                                    text = "🔧 MCP Server (filesystem)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                        
                        // Server info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MCP сервер:",
                                fontSize = 12.sp,
                                color = Color(0xFF666666)
                            )
                            Text(
                                text = "@modelcontextprotocol/server-filesystem",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF6A0DAD),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                        
                        // Why proxy
                        Text(
                            text = "💡 Прокси нужен, т.к. MCP серверы работают через stdio, недоступный в Android",
                            fontSize = 11.sp,
                            color = Color(0xFF999999),
                            lineHeight = 14.sp
                        )
                    }
                }
                
                // Tools List
                if (state.tools.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Доступные инструменты (${state.tools.size})",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6A0DAD),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            // Список инструментов без LazyColumn
                            state.tools.forEach { tool ->
                                ToolCard(tool = tool)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(tool: McpTool) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF5F5F5),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = tool.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6A0DAD)
            )
            
            if (tool.description.isNotEmpty()) {
                Text(
                    text = tool.description,
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    lineHeight = 18.sp
                )
            }
            
            if (tool.inputSchema.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFEEEEEE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Schema: ${tool.inputSchema.take(100)}${if (tool.inputSchema.length > 100) "..." else ""}",
                        fontSize = 11.sp,
                        color = Color(0xFF999999),
                        modifier = Modifier.padding(8.dp),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}
