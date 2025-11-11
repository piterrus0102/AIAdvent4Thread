package ru.piterrus.aiadvent4thread.data.repository

import kotlinx.coroutines.flow.Flow
import ru.piterrus.aiadvent4thread.data.model.ChatMessage
import ru.piterrus.aiadvent4thread.data.model.ResponseMode
import ru.piterrus.aiadvent4thread.data.model.YandexGPTFixedResponse

/**
 * Интерфейс репозитория для работы с чатом
 * Платформо-независимый интерфейс
 */
interface IChatRepository {
    /**
     * Все сообщения из БД как Flow
     */
    val allMessages: Flow<List<ChatMessage>>
    
    /**
     * Получить сообщения для конкретного режима
     * @param mode Режим чата
     * @return Flow списка сообщений для данного режима
     */
    fun getMessagesByMode(mode: ResponseMode): Flow<List<ChatMessage>>
    
    /**
     * Сохранить сообщение в БД
     * @return ID сохраненного сообщения
     */
    suspend fun saveMessage(message: ChatMessage): Long
    
    /**
     * Получить сообщение по ID
     */
    suspend fun getMessageById(id: Long): ChatMessage?
    
    /**
     * Очистить всю историю
     */
    suspend fun clearHistory()
    
    /**
     * Очистить историю для конкретного режима
     * @param mode Режим чата
     */
    suspend fun clearHistoryForMode(mode: ResponseMode)
    
    /**
     * Сохранить результаты поиска для сообщения
     */
    suspend fun saveSearchResults(messageId: Long, results: List<YandexGPTFixedResponse>)
    
    /**
     * Получить результаты поиска для сообщения
     */
    fun getSearchResults(messageId: Long): Flow<List<YandexGPTFixedResponse>>
}

