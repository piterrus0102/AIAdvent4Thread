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
    val tokensCount: Int? = null  // Количество токенов этого сообщения
)

