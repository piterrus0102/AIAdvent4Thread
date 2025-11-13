package ru.piterrus.aiadvent4thread.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val responseMode: Int = 0,
    val rawResponse: String? = null,
    val tokensCount: Int? = null,  // Количество токенов этого сообщения
    val totalTokens: Int? = null,  // Общее количество токенов (input + completion)
    val isSummary: Boolean = false,  // Флаг: является ли это сообщение результатом сжатия
    val tokensBeforeCompression: Int? = null  // Сколько токенов было до сжатия (для сжатых сообщений)
)

