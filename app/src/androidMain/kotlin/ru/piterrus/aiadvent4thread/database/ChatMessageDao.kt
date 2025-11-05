package ru.piterrus.aiadvent4thread.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>
    
    @Query("SELECT * FROM chat_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): ChatMessageEntity?
    
    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long
    
    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}

