package ru.piterrus.aiadvent4thread.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.piterrus.aiadvent4thread.ChatMessage
import ru.piterrus.aiadvent4thread.YandexGPTFixedResponse

class ChatRepository(
    private val messageDao: ChatMessageDao,
    private val searchResultDao: SearchResultDao
) {
    
    // Получаем все сообщения из БД и конвертируем в ChatMessage
    val allMessages: Flow<List<ChatMessage>> = messageDao.getAllMessages().map { entities ->
        entities.map { entity ->
            ChatMessage(
                id = entity.id,
                text = entity.text,
                isUser = entity.isUser,
                timestamp = entity.timestamp,
                isFixedResponseEnabled = entity.isFixedResponseEnabled,
                rawResponse = entity.rawResponse
            )
        }
    }
    
    // Сохраняем сообщение в БД и возвращаем id
    suspend fun saveMessage(message: ChatMessage): Long {
        val entity = ChatMessageEntity(
            id = message.id,
            text = message.text,
            isUser = message.isUser,
            timestamp = message.timestamp,
            isFixedResponseEnabled = message.isFixedResponseEnabled,
            rawResponse = message.rawResponse
        )
        return messageDao.insertMessage(entity)
    }
    
    // Сохраняем результаты поиска для сообщения
    suspend fun saveSearchResults(messageId: Long, results: List<YandexGPTFixedResponse>) {
        val entities = results.map { result ->
            SearchResultEntity(
                messageId = messageId,
                title = result.title,
                message = result.message
            )
        }
        searchResultDao.insertResults(entities)
    }
    
    // Получаем результаты поиска для сообщения
    fun getSearchResults(messageId: Long): Flow<List<YandexGPTFixedResponse>> {
        return searchResultDao.getResultsForMessage(messageId).map { entities ->
            entities.map { entity ->
                YandexGPTFixedResponse(
                    title = entity.title,
                    message = entity.message
                )
            }
        }
    }
    
    // Получаем сообщение по id
    suspend fun getMessageById(messageId: Long): ChatMessage? {
        return messageDao.getMessageById(messageId)?.let { entity ->
            ChatMessage(
                id = entity.id,
                text = entity.text,
                isUser = entity.isUser,
                timestamp = entity.timestamp,
                isFixedResponseEnabled = entity.isFixedResponseEnabled,
                rawResponse = entity.rawResponse
            )
        }
    }
    
    // Очищаем всю историю
    suspend fun clearHistory() {
        searchResultDao.clearAllResults()
        messageDao.clearAllMessages()
    }
}

