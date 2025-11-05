package ru.piterrus.aiadvent4thread.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "search_results",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["messageId"])]
)
data class SearchResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: Long, // связь с сообщением
    val title: String,
    val message: String
)

