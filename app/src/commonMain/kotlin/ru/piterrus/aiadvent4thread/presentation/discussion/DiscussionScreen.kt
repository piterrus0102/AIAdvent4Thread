package ru.piterrus.aiadvent4thread.presentation.discussion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.piterrus.aiadvent4thread.data.model.ExpertRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscussionScreen(
    state: DiscussionScreenState,
    onIntent: (DiscussionScreenIntent) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎭 Экспертная дискуссия") },
                navigationIcon = {
                    IconButton(onClick = { onIntent(DiscussionScreenIntent.BackClicked) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF6A0DAD),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Поле ввода темы
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Введите тему для обсуждения",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6A0DAD)
                    )
                    
                    OutlinedTextField(
                        value = state.topic,
                        onValueChange = { onIntent(DiscussionScreenIntent.TopicChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Например: Использование ИИ в образовании") },
                        enabled = !state.isLoadingRoles && state.roles.isEmpty(),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    Button(
                        onClick = { onIntent(DiscussionScreenIntent.StartDiscussion) },
                        enabled = state.topic.isNotBlank() && !state.isLoadingRoles && state.roles.isEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6A0DAD)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.isLoadingRoles) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (state.isLoadingRoles) "Обработка..." else "🚀 Начать дискуссию",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (state.roles.isNotEmpty()) {
                        Button(
                            onClick = { onIntent(DiscussionScreenIntent.ResetDiscussion) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF7F50)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🔄 Начать заново", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            // Ошибка
            if (state.errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEBEE)
                    )
                ) {
                    Text(
                        text = state.errorMessage,
                        modifier = Modifier.padding(16.dp),
                        color = Color(0xFFC62828),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Роли экспертов последовательно
            if (state.roles.isNotEmpty()) {
                Text(
                    text = "Эксперты",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                
                // Все три эксперта идут друг за другом
                state.roles.forEachIndexed { index, role ->
                    ExpertCard(
                        role = role,
                        expertNumber = index + 1,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onIntent(DiscussionScreenIntent.ExpertClicked(role, index + 1)) }
                    )
                }
            }
            
            // Итоговая суммаризация - показываем когда все эксперты ответили
            val allExpertsAnswered = state.roles.isNotEmpty() && state.roles.all { it.answer.isNotEmpty() && !it.isLoading }
            if ((state.summary.isNotEmpty() || state.isLoadingSummary) && allExpertsAnswered) {
                Text(
                    text = "Итоговый анализ",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🏆",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = "Модератор",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF7F50)
                            )
                        }
                        
                        HorizontalDivider(color = Color(0xFFFF7F50).copy(alpha = 0.3f))
                        
                        if (state.isLoadingSummary) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(color = Color(0xFF6A0DAD))
                                    Text(
                                        text = "Анализирую ответы экспертов...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        } else if (state.summary.isNotEmpty()) {
                            Text(
                                text = state.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF333333)
                            )
                        } else {
                            Text(
                                text = "Ожидание завершения анализа...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpertCard(
    role: ExpertRole,
    expertNumber: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val cardColor = when (expertNumber) {
        1 -> Color(0xFF4CAF50) // Зеленый
        2 -> Color(0xFF2196F3) // Синий
        3 -> Color(0xFFFFC107) // Желтый
        else -> Color.White
    }
    
    Card(
        modifier = modifier
            .then(
                if (role.answer.isNotEmpty() && !role.isLoading) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Заголовок с номером
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = cardColor
            ) {
                Text(
                    text = "Эксперт #$expertNumber",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            // Название роли
            Text(
                text = role.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cardColor
            )
            
            // Описание роли
            Text(
                text = role.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            
            HorizontalDivider(color = cardColor.copy(alpha = 0.3f))
            
            // Ответ
            if (role.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = cardColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else if (role.answer.isNotEmpty()) {
                // Ограничиваем текст 300 символами
                val displayText = if (role.answer.length > 300) {
                    role.answer.take(300) + "..."
                } else {
                    role.answer
                }
                
                Column {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF333333),
                        maxLines = Int.MAX_VALUE
                    )
                    
                    if (role.answer.length > 300) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Нажмите для полного ответа →",
                            style = MaterialTheme.typography.bodySmall,
                            color = cardColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Text(
                    text = "Ожидание ответа...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

