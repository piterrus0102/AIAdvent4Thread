package ru.piterrus.aiadvent4thread.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.piterrus.aiadvent4thread.ChatMessage

class ChatRepository(private val dao: ChatMessageDao) {
    
    // Получаем все сообщения из БД и конвертируем в ChatMessage
    val allMessages: Flow<List<ChatMessage>> = dao.getAllMessages().map { entities ->
        entities.map { entity ->
            ChatMessage(
                text = entity.text,
                isUser = entity.isUser,
                timestamp = entity.timestamp
            )
        }
    }
    
    // Сохраняем сообщение в БД
    suspend fun saveMessage(message: ChatMessage) {
        dao.insertMessage(
            ChatMessageEntity(
                text = message.text,
                isUser = message.isUser,
                timestamp = message.timestamp
            )
        )
    }
    
    // Очищаем всю историю
    suspend fun clearHistory() {
        dao.clearAllMessages()
    }
    
    // Проверяем есть ли сообщения
    suspend fun hasMessages(): Boolean {
        return dao.getMessageCount() > 0
    }
}

