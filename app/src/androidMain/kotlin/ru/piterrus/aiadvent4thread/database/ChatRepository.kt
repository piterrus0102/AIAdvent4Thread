package ru.piterrus.aiadvent4thread.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.piterrus.aiadvent4thread.data.model.ChatMessage
import ru.piterrus.aiadvent4thread.data.model.ResponseMode
import ru.piterrus.aiadvent4thread.data.model.YandexGPTFixedResponse
import ru.piterrus.aiadvent4thread.data.repository.IChatRepository

class ChatRepository(
    private val messageDao: ChatMessageDao,
    private val searchResultDao: SearchResultDao
) : IChatRepository {
    
    // Получаем все сообщения из БД и конвертируем в ChatMessage
    override val allMessages: Flow<List<ChatMessage>> = messageDao.getAllMessages().map { entities ->
        entities.map { entity ->
            ChatMessage(
                id = entity.id,
                text = entity.text,
                isUser = entity.isUser,
                timestamp = entity.timestamp,
                responseMode = ResponseMode.fromInt(entity.responseMode),
                rawResponse = entity.rawResponse,
                tokensCount = entity.tokensCount
            )
        }
    }
    
    // Получаем сообщения для конкретного режима
    override fun getMessagesByMode(mode: ResponseMode): Flow<List<ChatMessage>> {
        return messageDao.getMessagesByMode(mode.value).map { entities ->
            entities.map { entity ->
                ChatMessage(
                    id = entity.id,
                    text = entity.text,
                    isUser = entity.isUser,
                    timestamp = entity.timestamp,
                    responseMode = ResponseMode.fromInt(entity.responseMode),
                    rawResponse = entity.rawResponse,
                    tokensCount = entity.tokensCount
                )
            }
        }
    }
    
    // Сохраняем сообщение в БД и возвращаем id
    override suspend fun saveMessage(message: ChatMessage): Long {
        val entity = ChatMessageEntity(
            id = message.id,
            text = message.text,
            isUser = message.isUser,
            timestamp = message.timestamp,
            responseMode = message.responseMode.value,
            rawResponse = message.rawResponse,
            tokensCount = message.tokensCount
        )
        return messageDao.insertMessage(entity)
    }
    
    // Обновляем существующее сообщение в БД
    override suspend fun updateMessage(message: ChatMessage) {
        val entity = ChatMessageEntity(
            id = message.id,
            text = message.text,
            isUser = message.isUser,
            timestamp = message.timestamp,
            responseMode = message.responseMode.value,
            rawResponse = message.rawResponse,
            tokensCount = message.tokensCount
        )
        messageDao.updateMessage(entity)
    }
    
    // Сохраняем результаты поиска для сообщения
    override suspend fun saveSearchResults(messageId: Long, results: List<YandexGPTFixedResponse>) {
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
    override fun getSearchResults(messageId: Long): Flow<List<YandexGPTFixedResponse>> {
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
    override suspend fun getMessageById(messageId: Long): ChatMessage? {
        return messageDao.getMessageById(messageId)?.let { entity ->
            ChatMessage(
                id = entity.id,
                text = entity.text,
                isUser = entity.isUser,
                timestamp = entity.timestamp,
                responseMode = ResponseMode.fromInt(entity.responseMode),
                rawResponse = entity.rawResponse,
                tokensCount = entity.tokensCount
            )
        }
    }
    
    // Очищаем всю историю
    override suspend fun clearHistory() {
        searchResultDao.clearAllResults()
        messageDao.clearAllMessages()
    }
    
    // Очищаем историю для конкретного режима
    override suspend fun clearHistoryForMode(mode: ResponseMode) {
        // Сначала удаляем результаты поиска для всех сообщений этого режима
        // (в текущей реализации нет прямой связи, поэтому очищаем все результаты)
        // TODO: В будущем можно добавить связь через JOIN
        messageDao.clearMessagesByMode(mode.value)
    }
}

