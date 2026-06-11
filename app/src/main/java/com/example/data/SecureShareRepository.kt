package com.example.data

import kotlinx.coroutines.flow.Flow

class SecureShareRepository(
    private val friendDao: FriendDao,
    private val messageDao: MessageDao
) {
    val allFriends: Flow<List<Friend>> = friendDao.getAllFriendsFlow()

    fun getMessagesForFriend(friendId: String): Flow<List<Message>> {
        return messageDao.getMessagesForFriendFlow(friendId)
    }

    suspend fun getFriendById(friendId: String): Friend? {
        return friendDao.getFriendById(friendId)
    }

    suspend fun insertFriends(friends: List<Friend>) {
        friendDao.insertFriends(friends)
    }

    suspend fun updateFriend(friend: Friend) {
        friendDao.updateFriend(friend)
    }

    suspend fun updateFriendLocation(friendId: String, lat: Double, lng: Double, speed: Double, battery: Int) {
        friendDao.updateFriendLocation(friendId, lat, lng, speed, battery)
    }

    suspend fun insertMessage(message: Message) {
        messageDao.insertMessage(message)
    }

    suspend fun incrementUnreadCount(friendId: String) {
        friendDao.incrementUnreadCount(friendId)
    }

    suspend fun clearUnreadCount(friendId: String) {
        friendDao.clearUnreadCount(friendId)
    }
}
