package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends")
    fun getAllFriendsFlow(): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE id = :friendId LIMIT 1")
    suspend fun getFriendById(friendId: String): Friend?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriends(friends: List<Friend>)

    @Update
    suspend fun updateFriend(friend: Friend)

    @Query("UPDATE friends SET latitude = :lat, longitude = :lng, speedKmh = :speed, batteryPercent = :battery WHERE id = :friendId")
    suspend fun updateFriendLocation(friendId: String, lat: Double, lng: Double, speed: Double, battery: Int)

    @Query("UPDATE friends SET unreadCount = unreadCount + 1 WHERE id = :friendId")
    suspend fun incrementUnreadCount(friendId: String)

    @Query("UPDATE friends SET unreadCount = 0 WHERE id = :friendId")
    suspend fun clearUnreadCount(friendId: String)
}
