package ru.piterrus.aiadvent4thread.presentation.serverchat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerChatScreen(
    state: ServerChatScreenState,
    onIntent: (ServerChatScreenIntent) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current

    // Показываем Snackbar
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Auto-scroll при новых сообщениях
    LaunchedEffect(state.shouldScrollToBottom) {
        if (state.shouldScrollToBottom && state.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(state.messages.size - 1)
                onIntent(ServerChatScreenIntent.ScrolledToBottom)
            }
        }
    }

    // Диалог авторизации GitHub
    if (state.showGitHubAuthDialog) {
        GitHubAuthDialog(
            githubToken = state.githubToken,
            onTokenChange = { onIntent(ServerChatScreenIntent.GitHubTokenChanged(it)) },
            onConfirm = { onIntent(ServerChatScreenIntent.GitHubAuthConfirmed) },
            onDismiss = { onIntent(ServerChatScreenIntent.GitHubAuthDialogDismissed) }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "🖥️ Server Chat (MCP)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (state.isConnected) "🟢 YandexGPT" else "🔴 Не подключено",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onIntent(ServerChatScreenIntent.BackClicked) },
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
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Кнопка очистки чата
                        IconButton(
                            onClick = { onIntent(ServerChatScreenIntent.ClearChatClicked) },
                            enabled = state.isConnected && !state.isLoading
                        ) {
                            Text(
                                text = "🗑",
                                fontSize = 20.sp,
                                color = Color.White
                            )
                        }
                        
                        // Тумблер GitHub MCP
                        Text(
                            text = "GitHub",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = state.useGitHubMCP,
                            onCheckedChange = { enabled ->
                                onIntent(ServerChatScreenIntent.GitHubToggleChanged(enabled))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF4CAF50),
                                checkedTrackColor = Color(0xFF81C784),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1976D2)
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
                            Color(0xFF1976D2),
                            Color(0xFF42A5F5),
                            Color(0xFF90CAF9)
                        )
                    )
                )
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Сообщение об ошибке
                if (state.errorMessage != null) {
                    Surface(
                        color = Color(0xFFFFCDD2),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.errorMessage,
                                color = Color(0xFFC62828),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (!state.isConnected) {
                                TextButton(onClick = { onIntent(ServerChatScreenIntent.CheckConnection) }) {
                                    Text("Повторить", color = Color(0xFFC62828))
                                }
                            }
                        }
                    }
                }

                // Список сообщений
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (state.messages.isEmpty()) {
                        // Пустой экран с инструкциями
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🚀",
                                fontSize = 64.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "MCP-чат",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "📱 App → 🖥️  Server → 🤖 LLM",
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                    Text(
                                        text = "         ↕️",
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                    Text(
                                        text = "      🔌 MCP Client ↔️ 🔧 MCP Server",
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.messages) { message ->
                                MessageBubble(
                                    message = message,
                                    onLongClick = { text ->
                                        clipboardManager.setText(AnnotatedString(text))
                                        onIntent(ServerChatScreenIntent.CopyMessageToClipboard(text))
                                    }
                                )
                            }
                        }
                    }

                    // Индикатор загрузки
                    if (state.isLoading) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.95f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF1976D2)
                                )
                                Text(
                                    text = "Сервер обрабатывает запрос...",
                                    fontSize = 13.sp,
                                    color = Color(0xFF1976D2),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Поле ввода
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.inputText,
                            onValueChange = { onIntent(ServerChatScreenIntent.InputTextChanged(it)) },
                            placeholder = {
                                Text(
                                    text = if (state.isConnected) "Напишите сообщение..." else "Сервер недоступен",
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isLoading && state.isConnected,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1976D2),
                                focusedLabelColor = Color(0xFF1976D2),
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )

                        Button(
                            onClick = { onIntent(ServerChatScreenIntent.SendMessageClicked) },
                            enabled = state.inputText.isNotBlank() && !state.isLoading && state.isConnected,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1976D2)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.size(56.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = "➤",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubAuthDialog(
    githubToken: String,
    onTokenChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "GitHub Token",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Введите GitHub Personal Access Token",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                
                OutlinedTextField(
                    value = githubToken,
                    onValueChange = onTokenChange,
                    label = { Text("GitHub Token") },
                    placeholder = { Text("ghp_xxxxxxxxxxxx...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Text(
                    text = "💡 Создайте токен: github.com/settings/tokens\nНужны права: repo, read:org",
                    fontSize = 12.sp,
                    color = Color(0xFF2196F3),
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = githubToken.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1976D2)
                )
            ) {
                Text("Подключить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ServerChatMessage,
    onLongClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            color = if (message.isUser) Color(0xFF1976D2) else Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = { },
                    onLongClick = { onLongClick(message.text) }
                )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.text,
                    color = if (message.isUser) Color.White else Color.Black,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )

                // Если использовался инструмент, показываем бейдж
                if (message.toolUsed != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔧",
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Инструмент: ${message.toolUsed}",
                                fontSize = 11.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

