package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.EncryptionHelper
import com.example.data.AppDatabase
import com.example.data.Friend
import com.example.data.Message
import com.example.data.SecureShareRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.security.KeyPair
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class SecureShareViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = SecureShareRepository(db.friendDao(), db.messageDao())

    // App User Cryptographic Essentials
    val userKeyPair: KeyPair = EncryptionHelper.generateRSAKeyPair()
    val publicKeyString: String = EncryptionHelper.publicKeyToString(userKeyPair.public)

    // Store decrypted/resolved AES Chat Room keys in-memory
    private val activeSecretKeys = mutableMapOf<String, SecretKey>()

    // Selected active friend ID for messaging and routing
    private val _selectedFriendId = MutableStateFlow<String?>(null)
    val selectedFriendId: StateFlow<String?> = _selectedFriendId.asStateFlow()

    // Map visualization states
    private val _userLat = MutableStateFlow(37.7749)
    val userLat: StateFlow<Double> = _userLat.asStateFlow()

    private val _userLng = MutableStateFlow(-122.4194)
    val userLng: StateFlow<Double> = _userLng.asStateFlow()

    private val _mapScale = MutableStateFlow(1f)
    val mapScale: StateFlow<Float> = _mapScale.asStateFlow()

    private val _mapOffsetX = MutableStateFlow(0f)
    val mapOffsetX: StateFlow<Float> = _mapOffsetX.asStateFlow()

    private val _mapOffsetY = MutableStateFlow(0f)
    val mapOffsetY: StateFlow<Float> = _mapOffsetY.asStateFlow()

    // Debug cryptographic view options
    private val _rawEncryptedMode = MutableStateFlow(false)
    val rawEncryptedMode: StateFlow<Boolean> = _rawEncryptedMode.asStateFlow()

    // Active movement simulation toggle
    private val _isSimulatingMovement = MutableStateFlow(true)
    val isSimulatingMovement: StateFlow<Boolean> = _isSimulatingMovement.asStateFlow()

    // Connect DB Friends flow to view
    val friends: StateFlow<List<Friend>> = repository.allFriends
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Convert encrypted DB messages containing CipherText to decrypted lists reactively
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<DecryptedMessage>> = _selectedFriendId
        .flatMapLatest { friendId ->
            if (friendId == null) flowOf(emptyList())
            else repository.getMessagesForFriend(friendId).map { messages ->
                messages.map { msg ->
                    val plainText = getDecryptedText(msg)
                    DecryptedMessage(
                        id = msg.id,
                        friendId = msg.friendId,
                        isFromUser = msg.isFromUser,
                        plainText = plainText,
                        cipherText = msg.cipherText,
                        iv = msg.iv,
                        timestamp = msg.timestamp
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        seedInitialFriendsAndKeys()
        startMovementSimulation()
    }

    // Set map transform values
    fun transformMap(zoom: Float, panX: Float, panY: Float) {
        _mapScale.value = (_mapScale.value * zoom).coerceIn(0.2f, 5.0f)
        _mapOffsetX.value += panX
        _mapOffsetY.value += panY
    }

    // Reset Map coordinate tracking to center precisely on the Self node
    fun recenterMap() {
        _mapOffsetX.value = 0f
        _mapOffsetY.value = 0f
        _mapScale.value = 1f
    }

    // Select which friend is active in dashboard
    fun selectFriend(friendId: String?) {
        _selectedFriendId.value = friendId
        friendId?.let {
            viewModelScope.launch {
                repository.clearUnreadCount(it)
            }
        }
    }

    // Set Encryption inspection mode
    fun toggleRawEncryptedMode() {
        _rawEncryptedMode.value = !_rawEncryptedMode.value
    }

    // Set Simulated motion states
    fun toggleMovementSimulation() {
        _isSimulatingMovement.value = !_isSimulatingMovement.value
    }

    // Send high-security encrypted message
    fun sendEncryptedMessage(friendId: String, content: String) {
        if (content.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val key = getOrCreateSecretKeyForFriend(friendId)
            val encryptedPayload = EncryptionHelper.encryptAES(content, key)

            val message = Message(
                friendId = friendId,
                isFromUser = true,
                cipherText = encryptedPayload.cipherText,
                iv = encryptedPayload.iv
            )
            repository.insertMessage(message)

            // Simulate immediate automated E2EE responses from virtual Peer-to-Peer node after brief connection lag
            delay(1000)
            generateSimulatedFriendResponse(friendId, content)
        }
    }

    // Retrieve AES Room Key or Decrypt from store safely
    private suspend fun getOrCreateSecretKeyForFriend(friendId: String): SecretKey {
        activeSecretKeys[friendId]?.let { return it }

        val friend = repository.getFriendById(friendId)
        if (friend != null) {
            try {
                // In a production application, we'd decypher the key locally using the RSA Private Key
                // To simulate this in-memory in Jetpack Compose, we check if we already registered it.
                // If not, we generate a persistent session key and resolve:
                val decryptedKeyBytes = EncryptionHelper.decryptSecretKeyWithRSA(
                    friend.sessionAESKeyEncrypted,
                    userKeyPair.private
                )
                activeSecretKeys[friendId] = decryptedKeyBytes
                return decryptedKeyBytes
            } catch (e: Exception) {
                // Fallback generation matching the persistent encrypted structure
                val newKey = EncryptionHelper.generateAESKey()
                activeSecretKeys[friendId] = newKey
                return newKey
            }
        }
        val defKey = EncryptionHelper.generateAESKey()
        activeSecretKeys[friendId] = defKey
        return defKey
    }

    // Core decrypt function resolving cybertexts
    private fun getDecryptedText(message: Message): String {
        return try {
            val key = activeSecretKeys[message.friendId] ?: return "🔑 Secure payload (Tap to Decrypt)"
            EncryptionHelper.decryptAES(message.cipherText, message.iv, key)
        } catch (e: Exception) {
            "[Decryption Error]"
        }
    }

    private suspend fun generateSimulatedFriendResponse(friendId: String, userMessage: String) {
        val opponent = repository.getFriendById(friendId) ?: return
        val text = userMessage.lowercase()

        val responseContent = when {
            text.contains("where") || text.contains("location") || text.contains("map") -> {
                "My GPS is lock-active. I am running about ${opponent.speedKmh.toInt()} km/h around SOMA district!"
            }
            text.contains("key") || text.contains("secure") || text.contains("fingerprint") || text.contains("encrypt") -> {
                "Dynamic Diffie-Hellman keys generated! Secure signature is SHA256-RSA: ${opponent.publicKeyString.take(16)}..."
            }
            text.contains("hello") || text.contains("hi") || text.contains("hey") -> {
                "End-to-end encryption tunnel fully established. Hey! Where are you headed today?"
            }
            text.contains("battery") || text.contains("power") -> {
                "Power levels currently stable at ${opponent.batteryPercent}%! Battery saving mode active."
            }
            else -> {
                "Message received and decrypted successfully. Dynamic cipher integrity score: 100%!"
            }
        }

        val key = getOrCreateSecretKeyForFriend(friendId)
        val encryptedPayload = EncryptionHelper.encryptAES(responseContent, key)

        val responseMessage = Message(
            friendId = friendId,
            isFromUser = false,
            cipherText = encryptedPayload.cipherText,
            iv = encryptedPayload.iv
        )

        repository.insertMessage(responseMessage)

        if (_selectedFriendId.value != friendId) {
            repository.incrementUnreadCount(friendId)
        }
    }

    // Pre-seed three friends on first launch
    private fun seedInitialFriendsAndKeys() {
        viewModelScope.launch(Dispatchers.IO) {
            val dbCount = db.friendDao().getAllFriendsFlow().first().size
            if (dbCount == 0) {
                val mockFriends = listOf(
                    // Alice (North San Francisco)
                    createMockFriendCandidate("alice", "Alice Vance", 37.7820, -122.4110),
                    // Bob (West San Francisco)
                    createMockFriendCandidate("bob", "Bob Miller", 37.7710, -122.4330),
                    // Charlie (South-East Waterfront)
                    createMockFriendCandidate("charlie", "Charlie Smith", 37.7660, -122.4050)
                )
                repository.insertFriends(mockFriends)
            } else {
                // Populate active keys in memory from preexisting records
                val savedFriends = db.friendDao().getAllFriendsFlow().first()
                savedFriends.forEach { f ->
                    try {
                        val key = EncryptionHelper.decryptSecretKeyWithRSA(f.sessionAESKeyEncrypted, userKeyPair.private)
                        activeSecretKeys[f.id] = key
                    } catch (e: Exception) {
                        activeSecretKeys[f.id] = EncryptionHelper.generateAESKey()
                    }
                }
            }
        }
    }

    private fun createMockFriendCandidate(id: String, name: String, lat: Double, lng: Double): Friend {
        val guestKeyPair = EncryptionHelper.generateRSAKeyPair()
        val guestPublicKeyBase64 = EncryptionHelper.publicKeyToString(guestKeyPair.public)

        val uniqueSessionAESKey = EncryptionHelper.generateAESKey()
        // Encrypt the AES key with the USER'S RSA PUBLIC Key, so the user can decrypt it in-memory!
        val encryptedAESKey = EncryptionHelper.encryptSecretKeyWithRSA(uniqueSessionAESKey, userKeyPair.public)

        activeSecretKeys[id] = uniqueSessionAESKey

        return Friend(
            id = id,
            name = name,
            latitude = lat,
            longitude = lng,
            speedKmh = Random.nextDouble(0.0, 15.0),
            batteryPercent = Random.nextInt(40, 99),
            publicKeyString = guestPublicKeyBase64,
            sessionAESKeyEncrypted = encryptedAESKey
        )
    }

    // Continuous dynamic location wander simulation
    private fun startMovementSimulation() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (_isSimulatingMovement.value) {
                    val currentFriends = repository.allFriends.first()
                    currentFriends.forEach { friend ->
                        // Slight brownian motion wandering offsets
                        val deltaLat = (Random.nextDouble() - 0.5) * 0.0006
                        val deltaLng = (Random.nextDouble() - 0.5) * 0.0006

                        val newLat = friend.latitude + deltaLat
                        val newLng = friend.longitude + deltaLng
                        val newSpeed = if (Random.nextBoolean()) {
                            (friend.speedKmh + (Random.nextDouble() - 0.5) * 4.0).coerceIn(0.0, 45.0)
                        } else {
                            friend.speedKmh
                        }
                        val newBattery = (friend.batteryPercent - (if (Random.nextInt(100) < 5) 1 else 0)).coerceIn(1, 100)

                        repository.updateFriendLocation(friend.id, newLat, newLng, newSpeed, newBattery)
                    }
                }
                delay(3000)
            }
        }
    }
}

// Visual state UI mapping representation of messages
data class DecryptedMessage(
    val id: Int,
    val friendId: String,
    val isFromUser: Boolean,
    val plainText: String,
    val cipherText: String,
    val iv: String,
    val timestamp: Long
)
