package ru.piterrus.aiadvent4thread.presentation.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.piterrus.aiadvent4thread.data.model.ChatMessage
import ru.piterrus.aiadvent4thread.data.model.ResponseMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    state: ChatScreenState,
    onIntent: (ChatScreenIntent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    
    // Автоскролл вниз ТОЛЬКО когда последнее сообщение от пользователя
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            val lastMessage = state.messages.last()
            // Скроллим только если последнее сообщение от пользователя
            if (lastMessage.isUser) {
                kotlinx.coroutines.delay(50) // Небольшая задержка для отрисовки
                listState.animateScrollToItem(state.messages.size - 1)
            }
        }
    }
    
    // Дополнительный автоскролл вниз при необходимости (например, при возврате)
    LaunchedEffect(state.shouldScrollToBottom) {
        if (state.shouldScrollToBottom && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
            onIntent(ChatScreenIntent.ScrolledToBottom)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    // Показываем текущий режим
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = when (state.responseMode) {
                                ResponseMode.DEFAULT -> "💬 Чат"
                                ResponseMode.FIXED_RESPONSE_ENABLED -> "🔍 Поиск"
                                ResponseMode.TASK -> "📋 Задачи"
                                ResponseMode.TEMPERATURE_COMPARISON -> "🌡️ Температуры"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    // Кнопка возврата на стартовый экран
                    IconButton(
                        onClick = { onIntent(ChatScreenIntent.BackToStart) },
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
                    // Dropdown menu для выбора количества токенов padding
                    var showPaddingMenu by remember { mutableStateOf(false) }
                    
                    Box {
                        IconButton(
                            onClick = { showPaddingMenu = true },
                            enabled = !state.isLoading
                        ) {
                            Text("📦", style = MaterialTheme.typography.titleLarge)
                        }
                        
                        DropdownMenu(
                            expanded = showPaddingMenu,
                            onDismissRequest = { showPaddingMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("+16 000 токенов") },
                                onClick = {
                                    showPaddingMenu = false
                                    onIntent(ChatScreenIntent.SendContextPadding(16_000))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("+32 000 токенов") },
                                onClick = {
                                    showPaddingMenu = false
                                    onIntent(ChatScreenIntent.SendContextPadding(32_000))
                                }
                            )
                        }
                    }
                    
                    // Кнопка сжатия истории
                    IconButton(
                        onClick = { onIntent(ChatScreenIntent.CompressHistory) },
                        enabled = !state.isLoading && state.messages.size >= 10
                    ) {
                        Text("🗜️", style = MaterialTheme.typography.titleLarge)
                    }
                    
                    // Кнопка очистки истории
                    IconButton(onClick = { onIntent(ChatScreenIntent.ClearHistory) }) {
                        Text("🗑️", style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF6A0DAD), // Фиолетовый
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
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
                
                // Отображаем сообщения попарно с статистикой токенов
                val messages = state.messages
                var i = 0
                while (i < messages.size) {
                    val message = messages[i]
                    
                    item(key = "message_${message.id}") {
                        MessageBubble(
                            message = message,
                            onClick = { onIntent(ChatScreenIntent.MessageClicked(message)) },
                            onLongClick = { text ->
                                clipboardManager.setText(AnnotatedString(text))
                                onIntent(ChatScreenIntent.CopyMessageText(text))
                            },
                            onTemperatureResultClick = { index -> 
                                onIntent(ChatScreenIntent.TemperatureResultClicked(message, index))
                            }
                        )
                    }
                    
                    // Если это сжатое сообщение, показываем статистику сжатия
                    if (message.isSummary && message.tokensBeforeCompression != null && message.tokensCount != null) {
                        item(key = "compression_stats_${message.id}") {
                            CompressionStatisticsCard(
                                tokensBefore = message.tokensBeforeCompression,
                                tokensAfter = message.tokensCount
                            )
                        }
                    }
                    // Иначе, если это сообщение от агента (не пользователя) и есть статистика токенов, показываем её
                    else if (!message.isUser && message.totalTokens != null && message.tokensCount != null) {
                        // Находим предыдущее сообщение пользователя для расчета inputTokens
                        val userMessage = if (i > 0) messages[i - 1] else null
                        val inputTokens = userMessage?.tokensCount
                        
                        item(key = "tokens_${message.id}") {
                            TokensStatisticsCard(
                                inputTokens = inputTokens,
                                completionTokens = message.tokensCount,
                                totalTokens = message.totalTokens
                            )
                        }
                    }
                    
                    i++
                }
                
                // Элемент сравнения для последнего сообщения с температурным сравнением
                if (!state.isLoading && state.messages.isNotEmpty() && state.similarityAnalysis != null) {
                    val lastMessage = state.messages.last()
                    if (!lastMessage.isUser && 
                        lastMessage.responseMode == ResponseMode.TEMPERATURE_COMPARISON) {
                        item {
                            TemperatureComparisonCard(
                                analysisText = state.similarityAnalysis
                            )
                        }
                    }
                }
                
                if (state.isLoading) {
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
                        value = state.currentMessage,
                        onValueChange = { onIntent(ChatScreenIntent.MessageChanged(it)) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Введите сообщение...") },
                        enabled = !state.isLoading,
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )

                    Button(
                        onClick = { onIntent(ChatScreenIntent.SendMessage) },
                        enabled = !state.isLoading && state.currentMessage.isNotBlank(),
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
private fun TokensStatisticsCard(
    inputTokens: Int?,
    completionTokens: Int?,
    totalTokens: Int?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Input tokens
                if (inputTokens != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📥",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "$inputTokens",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6A0DAD)
                        )
                        Text(
                            text = "input",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF666666),
                            fontSize = 10.sp
                        )
                    }
                }
                
                // Completion tokens
                if (completionTokens != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📤",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "$completionTokens",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6A0DAD)
                        )
                        Text(
                            text = "output",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF666666),
                            fontSize = 10.sp
                        )
                    }
                }
                
                // Total tokens
                if (totalTokens != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🔤",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "$totalTokens",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF7F50)
                        )
                        Text(
                            text = "total",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF666666),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompressionStatisticsCard(
    tokensBefore: Int,
    tokensAfter: Int
) {
    val compressionRatio = ((tokensBefore - tokensAfter).toFloat() / tokensBefore.toFloat() * 100).toInt()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0) // Светло-оранжевый фон
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Заголовок
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🗜️",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Статистика сжатия",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF7F50)
                )
            }
            
            HorizontalDivider(color = Color(0xFFFFE0B2))
            
            // Статистика
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // До сжатия
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📦",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$tokensBefore",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6A0DAD)
                    )
                    Text(
                        text = "до",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF666666),
                        fontSize = 11.sp
                    )
                }
                
                // Стрелка
                Text(
                    text = "→",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFFF7F50),
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                // После сжатия
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📦",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$tokensAfter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "после",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF666666),
                        fontSize = 11.sp
                    )
                }
                
                // Разделитель
                VerticalDivider(
                    modifier = Modifier
                        .height(60.dp)
                        .padding(vertical = 4.dp),
                    color = Color(0xFFFFE0B2)
                )
                
                // Экономия
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "💾",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$compressionRatio%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF7F50)
                    )
                    Text(
                        text = "сжатие",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF666666),
                        fontSize = 11.sp
                    )
                }
            }
            
            // Подробности
            Text(
                text = "Экономия: ${tokensBefore - tokensAfter} токенов",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF666666),
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun TemperatureComparisonCard(
    analysisText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Заголовок
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📊",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Анализ консистентности температур",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6A0DAD)
                )
            }
            
            Divider(color = Color(0xFFE0E0E0))
            
            // Текст анализа от GPT
            Text(
                text = analysisText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF333333)
            )
            
            Divider(color = Color(0xFFE0E0E0))
            
            // Пояснение
            Text(
                text = "Анализ показывает, насколько стабильны ответы внутри каждой температуры. " +
                       "Высокий процент схожести = стабильные предсказуемые ответы. " +
                       "Низкий процент = разнообразные креативные ответы.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF666666),
                fontSize = 11.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    onClick: () -> Unit = {},
    onLongClick: (String) -> Unit = {},
    onTemperatureResultClick: (Int) -> Unit = {}
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
                        text = if (message.isSummary) "🗜️" else if (message.isUser) "👤" else "🤖",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = if (message.isSummary) "Резюме" else if (message.isUser) "Вы" else "YandexGPT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (message.isUser) {
                            Color.White
                        } else if (message.isSummary) {
                            Color(0xFFFF7F50) // Коралловый для сжатых сообщений
                        } else {
                            Color(0xFF6A0DAD) // Фиолетовый
                        }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                
                // Если сообщение с режимом сравнения температур, показываем три плашки
                if (!message.isUser && message.responseMode == ResponseMode.TEMPERATURE_COMPARISON && message.temperatureResults != null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        message.temperatureResults.forEachIndexed { index, result ->
                            Surface(
                                onClick = { onTemperatureResultClick(index) },
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
                                        text = "🌡️",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = result.shortQuery,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "Температура: ${result.temperature}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    }
                                    Text(
                                        text = "▶",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                } else if (!message.isUser && (message.responseMode == ResponseMode.FIXED_RESPONSE_ENABLED || message.responseMode == ResponseMode.TASK)) {
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
                    // Обычное текстовое сообщение с возможностью копирования
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.isUser) {
                            Color.White
                        } else {
                            Color(0xFF333333) // Темно-серый для лучшей читаемости
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = {
                                onLongClick(message.text)
                            }
                        )
                    )
                }
            }
        }
    }
}

