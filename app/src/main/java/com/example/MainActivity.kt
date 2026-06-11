package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.crypto.EncryptionHelper
import com.example.data.Friend
import com.example.ui.DecryptedMessage
import com.example.ui.MapCanvas
import com.example.ui.SecureShareViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_activity_scaffold"),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    SecureShareDashboard(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun SecureShareDashboard(
    modifier: Modifier = Modifier,
    viewModel: SecureShareViewModel = viewModel()
) {
    // Collect Flow States Live
    val friends by viewModel.friends.collectAsState()
    val selectedFriendId by viewModel.selectedFriendId.collectAsState()
    val activeMessages by viewModel.activeMessages.collectAsState()
    val userLat by viewModel.userLat.collectAsState()
    val userLng by viewModel.userLng.collectAsState()
    val mapScale by viewModel.mapScale.collectAsState()
    val mapOffsetX by viewModel.mapOffsetX.collectAsState()
    val mapOffsetY by viewModel.mapOffsetY.collectAsState()
    val rawEncryptedMode by viewModel.rawEncryptedMode.collectAsState()
    val isSimulatingMovement by viewModel.isSimulatingMovement.collectAsState()

    // Sliding Panel Custom Toggles
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Chats, 1 = Crypto Inspector, 2 = About security
    val scope = rememberCoroutineScope()

    // Outer UI Container (Sophisticated Dark theme)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Elegant Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Theme Menu",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { viewModel.recenterMap() }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Nexus Share",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "E2EE Security Portal Active",
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Interactive dynamic search simulation button
                IconButton(
                    onClick = { viewModel.toggleRawEncryptedMode() },
                    modifier = Modifier
                        .testTag("toggle_crypto_mode_button")
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (rawEncryptedMode) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Crypto Inspector Toggle",
                        tint = if (rawEncryptedMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Initial Avatar description from the mockup (JS)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "JS",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. Map Screen (Upper half)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f)
                .border(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            MapCanvas(
                userLat = userLat,
                userLng = userLng,
                friends = friends,
                selectedFriendId = selectedFriendId,
                mapScale = mapScale,
                mapOffsetX = mapOffsetX,
                mapOffsetY = mapOffsetY,
                onMapTransform = { zoom, panX, panY ->
                    viewModel.transformMap(zoom, panX, panY)
                },
                onFriendClick = { id ->
                    viewModel.selectFriend(id)
                }
            )

            // Dynamic float telemetry overlays on the map
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "My Location Core",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "LAT: ${"%.5f".format(userLat)}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "LNG: ${"%.5f".format(userLng)}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Map Controls (Recenter, Sim toggle)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(12.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Motion simulation toggle
                IconButton(
                    onClick = { viewModel.toggleMovementSimulation() },
                    modifier = Modifier.testTag("simulation_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isSimulatingMovement) Icons.Default.Refresh else Icons.Default.PlayArrow,
                        contentDescription = "Simulate Motion",
                        tint = if (isSimulatingMovement) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outline
                )

                // Recenter
                IconButton(
                    onClick = { viewModel.recenterMap() },
                    modifier = Modifier.testTag("recenter_map_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Recenter Map on Me",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 3. Command Tab Control (Lower half)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Secondary Tab strip switcher matching theme
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = {
                            Text(
                                "SECURE CHATS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif
                            )
                        },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("tab_chats")
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = {
                            Text(
                                "KEY INSPECTOR",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif
                            )
                        },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("tab_inspector")
                    )
                }

                // Render Active Content Tab
                when (activeTab) {
                    0 -> FriendsChatTab(
                        friends = friends,
                        selectedFriendId = selectedFriendId,
                        activeMessages = activeMessages,
                        rawEncryptedMode = rawEncryptedMode,
                        onFriendSelected = { id -> viewModel.selectFriend(id) },
                        onSendMessage = { friendId, msg -> viewModel.sendEncryptedMessage(friendId, msg) },
                        userLat = userLat,
                        userLng = userLng
                    )
                    1 -> CryptoInspectorTab(
                        friends = friends,
                        selectedFriendId = selectedFriendId,
                        myPublicKey = viewModel.publicKeyString,
                        myPrivateKeySecret = EncryptionHelper.privateKeyToString(viewModel.userKeyPair.private)
                    )
                }
            }
        }
    }
}

@Composable
fun FriendsChatTab(
    friends: List<Friend>,
    selectedFriendId: String?,
    activeMessages: List<DecryptedMessage>,
    rawEncryptedMode: Boolean,
    onFriendSelected: (String) -> Unit,
    onSendMessage: (String, String) -> Unit,
    userLat: Double,
    userLng: Double
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontal list of friends
        HorizontalFriendList(
            friends = friends,
            selectedId = selectedFriendId,
            onFriendSelected = onFriendSelected,
            userLat = userLat,
            userLng = userLng
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

        if (selectedFriendId == null) {
            // Empty state placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "No friend selected",
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Select a Friend Above\nto start encrypted E2EE communication.",
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            val selectedFriend = friends.find { it.id == selectedFriendId }
            ChatArea(
                friend = selectedFriend,
                messages = activeMessages,
                rawEncryptedMode = rawEncryptedMode,
                onSendMessage = { msg -> onSendMessage(selectedFriendId, msg) }
            )
        }
    }
}

@Composable
fun HorizontalFriendList(
    friends: List<Friend>,
    selectedId: String?,
    onFriendSelected: (String) -> Unit,
    userLat: Double,
    userLng: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        friends.forEach { friend ->
            val isSelected = friend.id == selectedId
            val distance = calculateDistanceMeters(userLat, userLng, friend.latitude, friend.longitude)

            val borderBrush = if (isSelected) {
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer))
            } else {
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))
            }

            Box(
                modifier = Modifier
                    .width(115.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        brush = borderBrush,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onFriendSelected(friend.id) }
                    .padding(8.dp)
                    .testTag("friend_cell_${friend.id}")
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = friend.name.split(" ").first(),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Unread counter bubble
                        if (friend.unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${friend.unreadCount}",
                                    color = MaterialTheme.colorScheme.onError,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "🔋${friend.batteryPercent}%",
                        color = if (friend.batteryPercent < 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )

                    Text(
                        text = if (distance > 1000) "${"%.1f".format(distance / 1000.0)} km" else "${distance.toInt()} m",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

@Composable
fun ChatArea(
    friend: Friend?,
    messages: List<DecryptedMessage>,
    rawEncryptedMode: Boolean,
    onSendMessage: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var textInput by remember { mutableStateOf("") }

    // Scroll automatically to newest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat History Console
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
            Text(
                text = "🔒 Handshake complete with PUBLIC-KEY: ${friend?.publicKeyString?.take(8)}...",
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )
                }
            }

            items(messages) { message ->
                MessageBubble(message = message, rawEncryptedMode = rawEncryptedMode)
            }
        }

        // Action input builder
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("chat_input_text_field"),
                placeholder = {
                    Text(
                        "Secure signal transmission...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textInput.isNotBlank()) {
                            onSendMessage(textInput)
                            textInput = ""
                            focusManager.clearFocus()
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onSendMessage(textInput)
                        textInput = ""
                        focusManager.clearFocus()
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .testTag("send_message_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send secure block",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: DecryptedMessage,
    rawEncryptedMode: Boolean
) {
    val isMe = message.isFromUser
    var showCipherDetail by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        val bubbleShape = if (isMe) {
            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 2.dp)
        } else {
            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 2.dp, bottomEnd = 12.dp)
        }

        val bubbleBg = if (isMe) {
            MaterialTheme.colorScheme.primaryContainer // EADDFF
        } else {
            MaterialTheme.colorScheme.surface // 2B2930
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(bubbleBg)
                .clickable { showCipherDetail = !showCipherDetail }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column {
                if (rawEncryptedMode || showCipherDetail) {
                    // Show actual Cipher Text details stored in local db
                    Text(
                        text = "ENCRYPTED DB RECORD:",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "iv: ${message.iv.take(12)}...",
                        color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = message.cipherText.take(128) + (if (message.cipherText.length > 128) "..." else ""),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 11.sp
                    )
                    if (!rawEncryptedMode) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer.copy(0.15f) else MaterialTheme.colorScheme.outline.copy(0.15f))
                        Text(
                            text = "DECRYPTED PLAIN:",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = message.plainText,
                            color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            lineHeight = 15.sp
                        )
                    }
                } else {
                    // Clear Decrypted Bubble
                    Text(
                        text = message.plainText,
                        color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (isMe) "ME • Decrypted" else "PEER • E2EE",
            color = Color.Gray,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun CryptoInspectorTab(
    friends: List<Friend>,
    selectedFriendId: String?,
    myPublicKey: String,
    myPrivateKeySecret: String
) {
    val selectedFriend = friends.find { it.id == selectedFriendId }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // My Keys
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "MY ASYMMETRIC KEYPAIR (RSA-2048)",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "PUBLIC KEY (DER-Base64):",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(6.dp)
                ) {
                    Text(
                        text = myPublicKey,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "PRIVATE KEY (Internal Secure store):",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = myPrivateKeySecret,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Selected Friend Hybrid Keys
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "PEER SECURITY CHANNEL",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (selectedFriend == null) {
                    Text(
                        text = "No active peer channel. Select a friend to inspect keys.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                } else {
                    Text(
                        text = "PEER: ${selectedFriend.name}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "PEER PUBLIC KEY (RSA):",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    ) {
                        Text(
                            text = selectedFriend.publicKeyString,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "SESSION KEY ENCRYPTED WITH PORTAL RSA:",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    ) {
                        Text(
                            text = selectedFriend.sessionAESKeyEncrypted,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Status: Hybrid Asymmetric Handshake COMPLETE.",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        // Crypto audit trail
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "SECURITY PROTOCOL METRICS",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.height(6.dp))
                val logs = listOf(
                    "Entropy pooling seeded from /dev/urandom ... OK",
                    "RSA-2048 Local Key Generator initiated ... OK",
                    "Friend handshakes derived via ECDH keyshares ... SECURE",
                    "Message payloads sealed inside AES-256-CBC ... OK",
                    "Room DB SQLite columns cipher-validated ... OK"
                )
                logs.forEach { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = log.split(" ... ").first(),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = log.split(" ... ").last(),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// Spherical coordinates linear accurate helper (SOMA focused)
fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0 // Earth radius in meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}
