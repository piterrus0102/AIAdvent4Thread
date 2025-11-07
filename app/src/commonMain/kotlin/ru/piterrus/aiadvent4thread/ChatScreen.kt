package ru.piterrus.aiadvent4thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: Long = 0,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val responseMode: ResponseMode = ResponseMode.DEFAULT,
    val rawResponse: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenUI(
    modifier: Modifier = Modifier,
    messages: List<ChatMessage>,
    currentMessage: String,
    isLoading: Boolean,
    responseMode: ResponseMode,
    onResponseModeToggle: (ResponseMode) -> Unit,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onClearHistory: () -> Unit = {},
    onMessageClick: (ChatMessage) -> Unit = {},
    shouldScrollToBottom: Boolean = false,
    onScrolledToBottom: () -> Unit = {},
    onNavigateToDiscussion: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Автоскролл вниз при изменении количества сообщений
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // Дополнительный автоскролл вниз при необходимости (например, при возврате)
    LaunchedEffect(shouldScrollToBottom) {
        if (shouldScrollToBottom && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
            onScrolledToBottom()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    // Переключатель режимов
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Кнопка DEFAULT
                        Surface(
                            onClick = { onResponseModeToggle(ResponseMode.DEFAULT) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (responseMode == ResponseMode.DEFAULT) 
                                Color(0xFFFF7F50) else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "💬 Чат",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                                fontWeight = if (responseMode == ResponseMode.DEFAULT) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        
                        // Кнопка FIXED_RESPONSE_ENABLED
                        Surface(
                            onClick = { onResponseModeToggle(ResponseMode.FIXED_RESPONSE_ENABLED) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (responseMode == ResponseMode.FIXED_RESPONSE_ENABLED) 
                                Color(0xFFFF7F50) else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "🔍 Поиск",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                                fontWeight = if (responseMode == ResponseMode.FIXED_RESPONSE_ENABLED) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        
                        // Кнопка TASK
                        Surface(
                            onClick = { onResponseModeToggle(ResponseMode.TASK) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (responseMode == ResponseMode.TASK) 
                                Color(0xFFFF7F50) else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "📋 Задачи",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                                fontWeight = if (responseMode == ResponseMode.TASK) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                },
                actions = {
                    // Кнопка экспертной дискуссии
                    IconButton(onClick = onNavigateToDiscussion) {
                        Text("🎭", style = MaterialTheme.typography.titleLarge)
                    }
                    // Кнопка очистки истории
                    IconButton(onClick = onClearHistory) {
                        Text("🗑️", style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF6A0DAD), // Фиолетовый
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding() // Всегда отступ от status bar
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF6A0DAD), // Фиолетовый
                            Color(0xFF8B3FA8), // Промежуточный
                            Color(0xFFFF7F50)  // Коралловый
                        )
                    )
                )
                .padding(paddingValues)
        ) {
            // Список сообщений
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                
                items(messages) { message ->
                    MessageBubble(
                        message = message,
                        onClick = { onMessageClick(message) }
                    )
                }
                
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Поле ввода с градиентом
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = currentMessage,
                        onValueChange = onMessageChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Введите сообщение...") },
                        enabled = !isLoading,
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )

                    Button(
                        onClick = {
                            onSendMessage()
                            coroutineScope.launch {
                                // Скролл к последнему сообщению
                                if (messages.isNotEmpty()) {
                                    listState.animateScrollToItem(messages.size)
                                }
                            }
                        },
                        enabled = !isLoading && currentMessage.isNotBlank(),
                        modifier = Modifier
                            .height(56.dp)
                            .widthIn(min = 100.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6A0DAD), // Фиолетовый
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF6A0DAD).copy(alpha = 0.5f),
                            disabledContentColor = Color.White.copy(alpha = 0.5f)
                        )
                    ) {
                        Text("📤 Отправить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    brush = if (message.isUser) {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF6A0DAD), // Фиолетовый
                                Color(0xFF8B3FA8)  // Темнее фиолетовый
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFF5F5F5)
                            )
                        )
                    },
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (message.isUser) 20.dp else 6.dp,
                        bottomEnd = if (message.isUser) 6.dp else 20.dp
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (message.isUser) "👤" else "🤖",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = if (message.isUser) "Вы" else "YandexGPT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (message.isUser) {
                            Color.White
                        } else {
                            Color(0xFF6A0DAD) // Фиолетовый
                        }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                
                // Если сообщение с включенным режимом поиска или задач, показываем кликабельную ссылку
                if (!message.isUser && (message.responseMode == ResponseMode.FIXED_RESPONSE_ENABLED || message.responseMode == ResponseMode.TASK)) {
                    Surface(
                        onClick = onClick,
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF7F50), // Коралловый
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = when (message.responseMode) {
                                    ResponseMode.FIXED_RESPONSE_ENABLED -> "🔍"
                                    ResponseMode.TASK -> "📋"
                                    else -> "🔍"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = when (message.responseMode) {
                                    ResponseMode.FIXED_RESPONSE_ENABLED -> "результаты поиска"
                                    ResponseMode.TASK -> "задачи"
                                    else -> "результаты"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "▶",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    // Обычное текстовое сообщение
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.isUser) {
                            Color.White
                        } else {
                            Color(0xFF333333) // Темно-серый для лучшей читаемости
                        }
                    )
                }
            }
        }
    }
}

