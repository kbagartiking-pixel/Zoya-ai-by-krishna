package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "assistant"
    val content: String,
    val languageTag: String = "en-IN",
    val commandBadge: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerAtMillis: Long,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false
)
