package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val friendId: String,
    val isFromUser: Boolean,
    val cipherText: String, // Clean Base64 content
    val iv: String,         // Crypto salt / Initialization Vector
    val timestamp: Long = System.currentTimeMillis()
)
