package ru.piterrus.aiadvent4thread

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StartScreen(
    onModeSelected: (ResponseMode) -> Unit,
    onDiscussionSelected: () -> Unit,
    onHuggingFaceSelected: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6A0DAD), // Фиолетовый
                        Color(0xFF8B3FA8), // Промежуточный
                        Color(0xFFFF7F50)  // Коралловый
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            // Заголовок
            Text(
                text = "Выберите режим",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            
            // Карточка "Чат"
            ModeCard(
                icon = "💬",
                title = "Чат",
                description = "Обычный режим общения с YandexGPT",
                onClick = { onModeSelected(ResponseMode.DEFAULT) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Карточка "Поиск"
            ModeCard(
                icon = "🔍",
                title = "Поиск",
                description = "Поиск информации с детальными результатами",
                onClick = { onModeSelected(ResponseMode.FIXED_RESPONSE_ENABLED) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Карточка "Задачи"
            ModeCard(
                icon = "📋",
                title = "Задачи",
                description = "Создание и управление задачами",
                onClick = { onModeSelected(ResponseMode.TASK) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Карточка "Экспертная дискуссия"
            ModeCard(
                icon = "🎭",
                title = "Экспертная дискуссия",
                description = "Обсуждение темы с виртуальными экспертами",
                onClick = onDiscussionSelected
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Карточка "Сравнение температур"
            ModeCard(
                icon = "🌡️",
                title = "Сравнение температур",
                description = "Три ответа с разными температурами LLM (0, 0.5, 1)",
                onClick = { onModeSelected(ResponseMode.TEMPERATURE_COMPARISON) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
        // Карточка "HuggingFace Models"
        ModeCard(
            icon = "🤗",
            title = "HuggingFace Models",
            description = "3 модели: L3-8B-Stheno, MiniMax-M2, Qwen2.5-7B",
            onClick = onHuggingFaceSelected
        )
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun ModeCard(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Иконка
            Text(
                text = icon,
                fontSize = 48.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            
            // Текст
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6A0DAD)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    lineHeight = 18.sp
                )
            }
            
            // Стрелка
            Text(
                text = "▶",
                fontSize = 24.sp,
                color = Color(0xFF6A0DAD)
            )
        }
    }
}

