package ru.piterrus.aiadvent4thread.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>
    
    @Query("SELECT * FROM chat_messages WHERE responseMode = :mode ORDER BY timestamp ASC")
    fun getMessagesByMode(mode: Int): Flow<List<ChatMessageEntity>>
    
    @Query("SELECT * FROM chat_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): ChatMessageEntity?
    
    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long
    
    @Update
    suspend fun updateMessage(message: ChatMessageEntity)
    
    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
    
    @Query("DELETE FROM chat_messages WHERE responseMode = :mode")
    suspend fun clearMessagesByMode(mode: Int)
}

