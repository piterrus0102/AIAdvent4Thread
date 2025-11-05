package ru.piterrus.aiadvent4thread.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchResultDao {
    
    @Query("SELECT * FROM search_results WHERE messageId = :messageId")
    fun getResultsForMessage(messageId: Long): Flow<List<SearchResultEntity>>
    
    @Insert
    suspend fun insertResults(results: List<SearchResultEntity>)
    
    @Query("DELETE FROM search_results")
    suspend fun clearAllResults()
}

