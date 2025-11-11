package ru.piterrus.aiadvent4thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class HFChatMessage(
    val text: String,
    val isUser: Boolean,
    val model: HFModel,
    val timeTaken: Long? = null,
    val tokensUsed: Int? = null,
    val thinkingContent: String? = null,  // Для Qwen3
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuggingFaceScreen(
    hfClient: HuggingFaceClient,
    onBackClick: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    var sthenoMessages by remember { mutableStateOf(listOf<HFChatMessage>()) }
    var miniMaxMessages by remember { mutableStateOf(listOf<HFChatMessage>()) }
    var qwen2Messages by remember { mutableStateOf(listOf<HFChatMessage>()) }
    var sthenoInput by remember { mutableStateOf("") }
    var miniMaxInput by remember { mutableStateOf("") }
    var qwen2Input by remember { mutableStateOf("") }
    var isSthenoLoading by remember { mutableStateOf(false) }
    var isMiniMaxLoading by remember { mutableStateOf(false) }
    var isQwen2Loading by remember { mutableStateOf(false) }
    var qwen2ThinkingMode by remember { mutableStateOf(true) }  // Переключатель thinking mode
    
    val coroutineScope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🤗 HuggingFace Models",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
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
        Column(
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
            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color(0xFF6A0DAD),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = Color(0xFFFF7F50)
                    )
                }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Text(
                            "L3-8B-Stheno",
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            "MiniMax-M2",
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = {
                        Text(
                            "Qwen2.5-7B",
                            fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                )
            }
            
            // Content for selected tab
            when (selectedTabIndex) {
                0 -> {
                    // Stheno Chat
                    HFModelChat(
                        messages = sthenoMessages,
                        currentInput = sthenoInput,
                        isLoading = isSthenoLoading,
                        modelName = "L3-8B-Stheno-v3.2",
                        modelDescription = "Продвинутая модель на базе Llama 3 (8B параметров)",
                        onInputChange = { sthenoInput = it },
                        onSendMessage = {
                            if (sthenoInput.isNotBlank()) {
                                val userPrompt = sthenoInput
                                sthenoInput = ""
                                
                                // Добавляем сообщение пользователя
                                sthenoMessages = sthenoMessages + HFChatMessage(
                                    text = userPrompt,
                                    isUser = true,
                                    model = HFModel.STHENO
                                )
                                
                                isSthenoLoading = true
                                coroutineScope.launch {
                                    try {
                                        val result = hfClient.callStheno(userPrompt)
                                        
                                        when (result) {
                                            is HuggingFaceResult.Success -> {
                                                sthenoMessages = sthenoMessages + HFChatMessage(
                                                    text = result.text,
                                                    isUser = false,
                                                    model = HFModel.STHENO,
                                                    timeTaken = result.timeTaken,
                                                    tokensUsed = result.tokensUsed
                                                )
                                            }
                                            is HuggingFaceResult.Error -> {
                                                sthenoMessages = sthenoMessages + HFChatMessage(
                                                    text = result.message,
                                                    isUser = false,
                                                    model = HFModel.STHENO
                                                )
                                            }
                                        }
                                    } finally {
                                        isSthenoLoading = false
                                    }
                                }
                            }
                        },
                        onClearHistory = {
                            sthenoMessages = emptyList()
                        }
                    )
                }
                1 -> {
                    // MiniMax Chat
                    HFModelChat(
                        messages = miniMaxMessages,
                        currentInput = miniMaxInput,
                        isLoading = isMiniMaxLoading,
                        modelName = "MiniMax-M2 (Novita)",
                        modelDescription = "Мультимодальная модель от MiniMaxAI через Novita AI",
                        onInputChange = { miniMaxInput = it },
                        onSendMessage = {
                            if (miniMaxInput.isNotBlank()) {
                                val userPrompt = miniMaxInput
                                miniMaxInput = ""
                                
                                // Добавляем сообщение пользователя
                                miniMaxMessages = miniMaxMessages + HFChatMessage(
                                    text = userPrompt,
                                    isUser = true,
                                    model = HFModel.MINIMAX
                                )
                                
                                isMiniMaxLoading = true
                                coroutineScope.launch {
                                    try {
                                        val result = hfClient.callMiniMax(userPrompt)
                                        
                                        when (result) {
                                            is HuggingFaceResult.Success -> {
                                                miniMaxMessages = miniMaxMessages + HFChatMessage(
                                                    text = result.text,
                                                    isUser = false,
                                                    model = HFModel.MINIMAX,
                                                    timeTaken = result.timeTaken,
                                                    tokensUsed = result.tokensUsed
                                                )
                                            }
                                            is HuggingFaceResult.Error -> {
                                                miniMaxMessages = miniMaxMessages + HFChatMessage(
                                                    text = result.message,
                                                    isUser = false,
                                                    model = HFModel.MINIMAX
                                                )
                                            }
                                        }
                                    } finally {
                                        isMiniMaxLoading = false
                                    }
                                }
                            }
                        },
                        onClearHistory = {
                            miniMaxMessages = emptyList()
                        }
                    )
                }
                2 -> {
                    // Qwen2.5 Chat with Thinking Mode
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Thinking Mode Toggle
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (qwen2ThinkingMode) "🧠 Thinking Mode" else "⚡ Fast Mode",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6A0DAD)
                                )
                                Switch(
                                    checked = qwen2ThinkingMode,
                                    onCheckedChange = { qwen2ThinkingMode = it }
                                )
                            }
                        }
                        
                        HFModelChat(
                            messages = qwen2Messages,
                            currentInput = qwen2Input,
                            isLoading = isQwen2Loading,
                            modelName = "Qwen2.5-7B" + if (qwen2ThinkingMode) " (Thinking)" else " (Fast)",
                            modelDescription = "Модель от Alibaba (7B параметров). Thinking mode: рассуждения + ответ",
                            onInputChange = { qwen2Input = it },
                            onSendMessage = {
                                if (qwen2Input.isNotBlank()) {
                                    val userPrompt = qwen2Input
                                    qwen2Input = ""
                                    
                                    qwen2Messages = qwen2Messages + HFChatMessage(
                                        text = userPrompt,
                                        isUser = true,
                                        model = HFModel.QWEN2
                                    )
                                    
                                    isQwen2Loading = true
                                    coroutineScope.launch {
                                        try {
                                            val result = hfClient.callQwen2(userPrompt, qwen2ThinkingMode)
                                            
                                            when (result) {
                                                is HuggingFaceResult.Success -> {
                                                    qwen2Messages = qwen2Messages + HFChatMessage(
                                                        text = result.text,
                                                        isUser = false,
                                                        model = HFModel.QWEN2,
                                                        timeTaken = result.timeTaken,
                                                        tokensUsed = result.tokensUsed,
                                                        thinkingContent = result.thinkingContent
                                                    )
                                                }
                                                is HuggingFaceResult.Error -> {
                                                    qwen2Messages = qwen2Messages + HFChatMessage(
                                                        text = result.message,
                                                        isUser = false,
                                                        model = HFModel.QWEN2
                                                    )
                                                }
                                            }
                                        } finally {
                                            isQwen2Loading = false
                                        }
                                    }
                                }
                            },
                            onClearHistory = {
                                qwen2Messages = emptyList()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HFModelChat(
    messages: List<HFChatMessage>,
    currentInput: String,
    isLoading: Boolean,
    modelName: String,
    modelDescription: String,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onClearHistory: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Автоскролл вниз при изменении количества сообщений
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Model info card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = modelName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6A0DAD)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = modelDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF666666)
                    )
                }
                
                IconButton(onClick = onClearHistory) {
                    Text("🗑️", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        
        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                HFMessageBubble(message)
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
        
        // Input field
        Surface(
            modifier = Modifier.fillMaxWidth(),
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
                    value = currentInput,
                    onValueChange = onInputChange,
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
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.size)
                            }
                        }
                    },
                    enabled = !isLoading && currentInput.isNotBlank(),
                    modifier = Modifier
                        .height(56.dp)
                        .widthIn(min = 100.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6A0DAD),
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

@Composable
fun HFMessageBubble(message: HFChatMessage) {
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
                                Color(0xFF6A0DAD),
                                Color(0xFF8B3FA8)
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
                        text = if (message.isUser) "Вы" else message.model.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (message.isUser) {
                            Color.White
                        } else {
                            Color(0xFF6A0DAD)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Показываем thinking content для Qwen3, если есть
                if (!message.isUser && message.thinkingContent != null && message.thinkingContent.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF0F0F0)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "🧠",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "Процесс рассуждения:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF666666)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = message.thinkingContent,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF666666),
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) {
                        Color.White
                    } else {
                        Color(0xFF333333)
                    }
                )
                
                // Метрики (время и токены)
                if (!message.isUser && (message.timeTaken != null || message.tokensUsed != null)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = Color(0xFFE0E0E0))
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.timeTaken != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⏱️",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "${message.timeTaken}ms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF666666),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        
                        if (message.tokensUsed != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔤",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "~${message.tokensUsed} токенов",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF666666),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    
                    // Стоимость (если модель платная)
                    if (message.tokensUsed != null) {
                        Text(
                            text = "💰 Бесплатно (HuggingFace API)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF00C853),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

