package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class Friend(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double,
    val batteryPercent: Int,
    val publicKeyString: String,
    val sessionAESKeyEncrypted: String, // AES chat key encrypted with Friend's RSA Public Key
    val trackingActive: Boolean = true,
    val unreadCount: Int = 0
)
