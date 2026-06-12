package com.example;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.crypto.EncryptionHelper;
import com.example.data.DatabaseHelper;
import com.example.data.Friend;
import com.example.data.Message;
import com.example.ui.MapCustomView;

import java.security.KeyPair;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.SecretKey;

public class MainActivity extends AppCompatActivity implements MapCustomView.OnFriendSelectedListener {

    // Database and Cryptography engines
    private DatabaseHelper dbHelper;
    private final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private KeyPair userKeyPair;
    private String userPublicKeyBase64;
    private final Map<String, SecretKey> activeSecretKeys = new HashMap<>();

    // Simulation loops
    private final Handler simulationHandler = new Handler(Looper.getMainLooper());
    private Runnable simulationRunnable;

    // View References
    private MapCustomView mapView;
    private TextView tvUserLat, tvUserLng;
    private ImageButton btnToggleSim, btnRecenter, btnCryptoMode;
    private TextView btnTabChats, btnTabInspector;
    
    private View viewTabChats;
    private ScrollView viewTabInspector;
    private View layoutEmptyState, layoutChatActive;
    
    private LinearLayout layoutFriendsList;
    private TextView tvHandshakeBanner;
    private ScrollView scrollMessages;
    private LinearLayout layoutMessagesContainer;
    
    private EditText etChatInput;
    private ImageButton btnSendChat;

    private TextView tvLocalPubKey, tvLocalPrivKey;
    private TextView tvPeerFallbackInfo;
    private LinearLayout layoutPeerInfoGroup;
    private TextView tvPeerChatWith, tvPeerPubKey, tvPeerSessionKey;
    private LinearLayout layoutAuditLogs;

    // Active Application states
    private String selectedFriendId = null;
    private boolean rawEncryptedMode = false;
    private boolean isSimulatingMovement = true;
    private double userLat = 37.7749;
    private double userLng = -122.4194;
    
    private final List<String> auditLogs = new ArrayList<>();
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // A. Setup Database Helper
        dbHelper = DatabaseHelper.getInstance(this);

        // B. Bind Master Views
        bindViews();

        // C. Generate Local RSA Identity base in background to avoid frame lags
        addAuditLog("LOCAL - Generating device security RSA Keys...");
        diskExecutor.execute(() -> {
            userKeyPair = EncryptionHelper.generateRSAKeyPair();
            userPublicKeyBase64 = EncryptionHelper.publicKeyToString(userKeyPair.getPublic());
            
            runOnUiThread(() -> {
                addAuditLog("LOCAL - RSA-2048 Identity KeyPair available.");
                // Update local keys in inspector UI
                tvLocalPubKey.setText(userPublicKeyBase64);
                tvLocalPrivKey.setText(EncryptionHelper.privateKeyToString(userKeyPair.getPrivate()));
                
                // Once Keys are ready, seed and launch
                seedInitialFriendsAndKeys();
            });
        });

        // D. Configure Interactive Actions listeners
        configureListeners();

        // E. Launch Simulated drift loop
        startBackgroundSimulations();
    }

    private void bindViews() {
        mapView = findViewById(R.id.map_view);
        tvUserLat = findViewById(R.id.tv_user_lat);
        tvUserLng = findViewById(R.id.tv_user_lng);
        btnToggleSim = findViewById(R.id.btn_toggle_sim);
        btnRecenter = findViewById(R.id.btn_recenter);
        btnCryptoMode = findViewById(R.id.btn_crypto_mode);
        
        btnTabChats = findViewById(R.id.btn_tab_chats);
        btnTabInspector = findViewById(R.id.btn_tab_inspector);
        viewTabChats = findViewById(R.id.view_tab_chats);
        viewTabInspector = findViewById(R.id.view_tab_inspector);
        
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        layoutChatActive = findViewById(R.id.layout_chat_active);
        
        layoutFriendsList = findViewById(R.id.layout_friends_list);
        tvHandshakeBanner = findViewById(R.id.tv_handshake_banner);
        scrollMessages = findViewById(R.id.scroll_messages);
        layoutMessagesContainer = findViewById(R.id.layout_messages_container);
        
        etChatInput = findViewById(R.id.et_chat_input);
        btnSendChat = findViewById(R.id.btn_send);

        tvLocalPubKey = findViewById(R.id.tv_local_pub_key);
        tvLocalPrivKey = findViewById(R.id.tv_local_priv_key);
        tvPeerFallbackInfo = findViewById(R.id.tv_peer_fallback_info);
        layoutPeerInfoGroup = findViewById(R.id.layout_peer_info_group);
        tvPeerChatWith = findViewById(R.id.tv_peer_chat_with);
        tvPeerPubKey = findViewById(R.id.tv_peer_pub_key);
        tvPeerSessionKey = findViewById(R.id.tv_peer_session_key);
        layoutAuditLogs = findViewById(R.id.layout_audit_logs);

        // Attach listener callback on mapCustomView
        mapView.setOnFriendSelectedListener(this);
    }

    private void configureListeners() {
        // Tab switching: SECURE CHATS
        btnTabChats.setOnClickListener(v -> {
            viewTabChats.setVisibility(View.VISIBLE);
            viewTabInspector.setVisibility(View.GONE);
            btnTabChats.setTextColor(getResources().getColor(R.color.sophisticated_primary));
            btnTabInspector.setTextColor(getResources().getColor(R.color.sophisticated_text_muted));
        });

        // Tab switching: KEY INSPECTOR
        btnTabInspector.setOnClickListener(v -> {
            viewTabChats.setVisibility(View.GONE);
            viewTabInspector.setVisibility(View.VISIBLE);
            btnTabInspector.setTextColor(getResources().getColor(R.color.sophisticated_primary));
            btnTabChats.setTextColor(getResources().getColor(R.color.sophisticated_text_muted));
            renderKeysInspector();
        });

        // Recenter Map Click
        btnRecenter.setOnClickListener(v -> {
            mapView.recenter();
            addAuditLog("MAP - Canvas viewport centered on user coordinates.");
        });

        // Toggle Simulation Play/Pause click
        btnToggleSim.setOnClickListener(v -> {
            isSimulatingMovement = !isSimulatingMovement;
            if (isSimulatingMovement) {
                btnToggleSim.setImageResource(R.drawable.ic_pause);
                addAuditLog("SIMULATOR - Tracking wander motion activated.");
            } else {
                btnToggleSim.setImageResource(R.drawable.ic_play);
                addAuditLog("SIMULATOR - Tracking wander motion frozen.");
            }
        });

        // Crypto Indicator Switcher
        btnCryptoMode.setOnClickListener(v -> {
            rawEncryptedMode = !rawEncryptedMode;
            if (rawEncryptedMode) {
                btnCryptoMode.setColorFilter(getResources().getColor(R.color.sophisticated_error));
                addAuditLog("SECURITY - Raw cipher monitoring enabled.");
            } else {
                btnCryptoMode.setColorFilter(getResources().getColor(R.color.sophisticated_primary));
                addAuditLog("SECURITY - Standard clear-text monitoring enabled.");
            }
            // re-render chat logs to apply encryption filters
            if (selectedFriendId != null) {
                loadChatLogAndRender(selectedFriendId);
            }
        });

        // Send encrypted message payload dispatcher
        btnSendChat.setOnClickListener(v -> {
            String content = etChatInput.getText().toString().trim();
            if (TextUtils.isEmpty(content) || selectedFriendId == null) return;
            
            etChatInput.setText("");
            sendEncryptedMessage(selectedFriendId, content);
        });
    }

    // Seeding default peers in our database
    private void seedInitialFriendsAndKeys() {
        diskExecutor.execute(() -> {
            List<Friend> list = dbHelper.getAllFriends();
            if (list.isEmpty()) {
                addAuditLog("DB - Seeding initial peer nodes...");
                List<Friend> newFriends = new ArrayList<>();
                newFriends.add(createMockFriendCandidate("alice", "Alice Vance", 37.7820, -122.4110));
                newFriends.add(createMockFriendCandidate("bob", "Bob Miller", 37.7710, -122.4330));
                newFriends.add(createMockFriendCandidate("charlie", "Charlie Smith", 37.7660, -122.4050));
                dbHelper.insertFriends(newFriends);
                addAuditLog("DB - Seeding process successfully stored 3 friends.");
            } else {
                addAuditLog("DB - Standard database has preexisting records.");
                // Update local in-memory keys
                for (Friend f : list) {
                    try {
                        SecretKey resolved = EncryptionHelper.decryptSecretKeyWithRSA(
                            f.sessionAESKeyEncrypted, userKeyPair.getPrivate());
                        activeSecretKeys.put(f.id, resolved);
                    } catch (Exception e) {
                        activeSecretKeys.put(f.id, EncryptionHelper.generateAESKey());
                    }
                }
            }
            refreshFriendsList();
        });
    }

    private Friend createMockFriendCandidate(String id, String name, double lat, double lng) {
        // Generate asymmetric peer RSA values
        KeyPair buddyKeyPair = EncryptionHelper.generateRSAKeyPair();
        String pubStr = EncryptionHelper.publicKeyToString(buddyKeyPair.getPublic());

        // Generate dynamic symmetric room key matching session
        SecretKey sessionAESKey = EncryptionHelper.generateAESKey();
        
        // Encrypt with our (User) RSA public key so we can decrypt it locally
        String encryptedAES = EncryptionHelper.encryptSecretKeyWithRSA(sessionAESKey, userKeyPair.getPublic());
        activeSecretKeys.put(id, sessionAESKey);
        
        addAuditLog(name + " - Symmetric key E2EE handshook.");

        return new Friend(
            id,
            name,
            lat,
            lng,
            random.nextDouble() * 12.0,
            random.nextInt(35) + 60,
            pubStr,
            encryptedAES,
            true,
            0
        );
    }

    // Callback on friend clicked from map Custom view
    @Override
    public void onFriendSelected(String friendId) {
        selectFriend(friendId);
    }

    private void selectFriend(String friendId) {
        selectedFriendId = friendId;
        addAuditLog("CHANNEL - Selecting crypt tunnel secure stream with ID: " + friendId);
        
        // Clear unread badge in database
        diskExecutor.execute(() -> {
            dbHelper.clearUnreadCount(friendId);
            refreshFriendsList();
            runOnUiThread(() -> {
                // Adjust views from placeholders to chat log view
                layoutEmptyState.setVisibility(View.GONE);
                layoutChatActive.setVisibility(View.VISIBLE);
                
                // Show dynamic handshake complete stats
                Friend activeFriend = dbHelper.getFriendById(friendId);
                if (activeFriend != null) {
                    String hashPrefix = activeFriend.publicKeyString.length() > 12 
                        ? activeFriend.publicKeyString.substring(0, 12) + "..." 
                        : activeFriend.publicKeyString;
                    tvHandshakeBanner.setText("🔒 Handshake complete with PUBLIC-KEY: " + hashPrefix);
                }
                
                // Redraw Map selected highlights
                mapView.setSelectedFriendId(friendId);
                
                // Load and render existing logs
                loadChatLogAndRender(friendId);
            });
        });
    }

    // Load Chat logs in UI
    private void loadChatLogAndRender(String friendId) {
        diskExecutor.execute(() -> {
            List<Message> messageList = dbHelper.getMessagesForFriend(friendId);
            runOnUiThread(() -> {
                layoutMessagesContainer.removeAllViews();
                
                for (Message m : messageList) {
                    LinearLayout bubbleContainer = new LinearLayout(this);
                    bubbleContainer.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    containerLp.setMargins(0, dpToPx(5), 0, dpToPx(5));
                    bubbleContainer.setLayoutParams(containerLp);
                    
                    View spacerLeft = new View(this);
                    LinearLayout.LayoutParams spacerLeftLp = new LinearLayout.LayoutParams(0, 1);
                    spacerLeftLp.weight = 1.0f;
                    spacerLeft.setLayoutParams(spacerLeftLp);
                    
                    View spacerRight = new View(this);
                    LinearLayout.LayoutParams spacerRightLp = new LinearLayout.LayoutParams(0, 1);
                    spacerRightLp.weight = 1.0f;
                    spacerRight.setLayoutParams(spacerRightLp);
                    
                    LinearLayout contentBubble = new LinearLayout(this);
                    contentBubble.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(
                            dpToPx(270), LinearLayout.LayoutParams.WRAP_CONTENT);
                    contentBubble.setLayoutParams(bubbleLp);
                    contentBubble.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
                    
                    if (m.isFromUser) {
                        contentBubble.setBackgroundResource(R.drawable.bg_card_selected);
                        bubbleContainer.addView(spacerLeft);
                        bubbleContainer.addView(contentBubble);
                    } else {
                        contentBubble.setBackgroundResource(R.drawable.bg_card_normal);
                        bubbleContainer.addView(contentBubble);
                        bubbleContainer.addView(spacerRight);
                    }
                    
                    // Cipher representation
                    TextView tvHeader = new TextView(this);
                    tvHeader.setText("ENCRYPTED DB RECORD:");
                    tvHeader.setTextSize(8);
                    tvHeader.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
                    tvHeader.setTextColor(getResources().getColor(R.color.sophisticated_error));
                    contentBubble.addView(tvHeader);
                    
                    TextView tvIvValue = new TextView(this);
                    tvIvValue.setText("iv: " + (m.iv.length() > 16 ? m.iv.substring(0, 16) + "..." : m.iv));
                    tvIvValue.setTextSize(8);
                    tvIvValue.setTypeface(android.graphics.Typeface.MONOSPACE);
                    tvIvValue.setTextColor(getResources().getColor(R.color.sophisticated_text_muted));
                    contentBubble.addView(tvIvValue);
                    
                    TextView tvCipherRepr = new TextView(this);
                    String cipherCut = m.cipherText;
                    if (cipherCut.length() > 96) {
                        cipherCut = cipherCut.substring(0, 96) + "...";
                    }
                    tvCipherRepr.setText(cipherCut);
                    tvCipherRepr.setTextSize(8);
                    tvCipherRepr.setTypeface(android.graphics.Typeface.MONOSPACE);
                    tvCipherRepr.setTextColor(getResources().getColor(R.color.sophisticated_error));
                    contentBubble.addView(tvCipherRepr);
                    
                    // Standard decryption row representation
                    if (!rawEncryptedMode) {
                        View dividerLine = new View(this);
                        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
                        divLp.setMargins(0, dpToPx(6), 0, dpToPx(6));
                        dividerLine.setLayoutParams(divLp);
                        dividerLine.setBackgroundColor(getResources().getColor(R.color.sophisticated_outline));
                        contentBubble.addView(dividerLine);
                        
                        TextView tvDecryptedLabel = new TextView(this);
                        tvDecryptedLabel.setText("DECRYPTED PLAIN:");
                        tvDecryptedLabel.setTextSize(8);
                        tvDecryptedLabel.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
                        tvDecryptedLabel.setTextColor(getResources().getColor(R.color.sophisticated_primary));
                        contentBubble.addView(tvDecryptedLabel);
                        
                        TextView tvPlainVal = new TextView(this);
                        String plainTextContent;
                        try {
                            SecretKey secretK = activeSecretKeys.get(friendId);
                            if (secretK != null) {
                                plainTextContent = EncryptionHelper.decryptAES(m.cipherText, m.iv, secretK);
                            } else {
                                plainTextContent = "🔒 Decryption key error matching record.";
                            }
                        } catch (Exception e) {
                            plainTextContent = "[Decryption Failure]";
                        }
                        
                        tvPlainVal.setText(plainTextContent);
                        tvPlainVal.setTextSize(12);
                        tvPlainVal.setTextColor(getResources().getColor(R.color.sophisticated_on_background));
                        contentBubble.addView(tvPlainVal);
                    }
                    
                    layoutMessagesContainer.addView(bubbleContainer);
                }
                
                // Pull logs scrolling bottom
                scrollMessages.post(() -> scrollMessages.fullScroll(View.FOCUS_DOWN));
            });
        });
    }

    private void sendEncryptedMessage(String friendId, String messageContent) {
        addAuditLog("AES - Local encryption dispatched with contents.");
        
        diskExecutor.execute(() -> {
            SecretKey key = activeSecretKeys.get(friendId);
            if (key == null) {
                key = EncryptionHelper.generateAESKey();
                activeSecretKeys.put(friendId, key);
            }
            
            EncryptionHelper.EncryptedPayload payload = EncryptionHelper.encryptAES(messageContent, key);
            Message msg = new Message(friendId, true, payload.cipherText, payload.iv);
            dbHelper.insertMessage(msg);
            
            runOnUiThread(() -> {
                loadChatLogAndRender(friendId);
                // Trigger automated E2EE peer simulator response
                mainHandler.postDelayed(() -> triggerSimulatedPeerResponse(friendId, messageContent), 1000);
            });
        });
    }

    private void triggerSimulatedPeerResponse(String friendId, String userIncomingText) {
        diskExecutor.execute(() -> {
            Friend peer = dbHelper.getFriendById(friendId);
            if (peer == null) return;
            
            String normalizedInputText = userIncomingText.toLowerCase().trim();
            final String responseString;
            
            if (normalizedInputText.contains("where") || normalizedInputText.contains("location") || normalizedInputText.contains("map")) {
                responseString = "My GPS is lock-active. I am running about " + (int)peer.speedKmh + " km/h around SOMA district!";
            } else if (normalizedInputText.contains("key") || normalizedInputText.contains("secure") || normalizedInputText.contains("fingerprint") || normalizedInputText.contains("encrypt")) {
                String subKey = peer.publicKeyString.length() > 16 ? peer.publicKeyString.substring(0, 16) : peer.publicKeyString;
                responseString = "Dynamic Diffie-Hellman keys generated! Secure signature is SHA256-RSA: " + subKey + "...";
            } else if (normalizedInputText.contains("hello") || normalizedInputText.contains("hi") || normalizedInputText.contains("hey")) {
                responseString = "End-to-end encryption tunnel fully established. Hey! Where are you headed today?";
            } else if (normalizedInputText.contains("battery") || normalizedInputText.contains("power")) {
                responseString = "Power levels currently stable at " + peer.batteryPercent + "%! Battery saving mode active.";
            } else {
                responseString = "Message received and decrypted successfully. Dynamic cipher integrity score: 100%!";
            }
            
            SecretKey key = activeSecretKeys.get(friendId);
            if (key == null) {
                key = EncryptionHelper.generateAESKey();
                activeSecretKeys.put(friendId, key);
            }
            
            EncryptionHelper.EncryptedPayload payload = EncryptionHelper.encryptAES(responseString, key);
            Message responseMsg = new Message(friendId, false, payload.cipherText, payload.iv);
            dbHelper.insertMessage(responseMsg);
            
            if (!friendId.equals(selectedFriendId)) {
                dbHelper.incrementUnreadCount(friendId);
            }
            
            runOnUiThread(() -> {
                addAuditLog("AES - Remote encryption decrypted from: " + peer.name.split(" ")[0]);
                refreshFriendsList();
                if (friendId.equals(selectedFriendId)) {
                    loadChatLogAndRender(friendId);
                }
            });
        });
    }

    private void refreshFriendsList() {
        diskExecutor.execute(() -> {
            List<Friend> list = dbHelper.getAllFriends();
            runOnUiThread(() -> {
                mapView.setFriends(list);
                mapView.setUserLocation(userLat, userLng);
                tvUserLat.setText(String.format(Locale.US, "LAT: %.5f", userLat));
                tvUserLng.setText(String.format(Locale.US, "LNG: %.5f", userLng));
                
                // Dynamically populate friend row cards
                layoutFriendsList.removeAllViews();
                for (Friend f : list) {
                    LinearLayout card = new LinearLayout(this);
                    card.setOrientation(LinearLayout.VERTICAL);
                    int widthPx = dpToPx(120);
                    int heightPx = dpToPx(76);
                    LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(widthPx, heightPx);
                    cardLp.setMargins(dpToPx(6), 0, dpToPx(6), 0);
                    card.setLayoutParams(cardLp);
                    card.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
                    
                    boolean isActive = f.id.equals(selectedFriendId);
                    card.setBackgroundResource(isActive ? R.drawable.bg_card_selected : R.drawable.bg_card_normal);
                    
                    // Name label text
                    TextView tvName = new TextView(this);
                    tvName.setText(f.name.split(" ")[0]);
                    tvName.setTextSize(12);
                    tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                    tvName.setTextColor(getResources().getColor(R.color.sophisticated_on_background));
                    card.addView(tvName);
                    
                    // Battery indicator
                    TextView tvBattery = new TextView(this);
                    tvBattery.setText("🔋 " + f.batteryPercent + "%");
                    tvBattery.setTextSize(9);
                    tvBattery.setTextColor(getResources().getColor(R.color.sophisticated_text_muted));
                    card.addView(tvBattery);
                    
                    // Distance indicator
                    TextView tvDist = new TextView(this);
                    double d = calculateDistance(userLat, userLng, f.latitude, f.longitude);
                    String meterString = d > 1.0 ? String.format(Locale.US, "%.1f km", d) : String.format(Locale.US, "%d m", (int)(d * 1000.0));
                    tvDist.setText(meterString + " | " + (int)f.speedKmh + " km/h");
                    tvDist.setTextSize(9);
                    tvDist.setTextColor(getResources().getColor(R.color.sophisticated_primary));
                    card.addView(tvDist);

                    // Unread badge Row
                    if (f.unreadCount > 0) {
                        TextView tvState = new TextView(this);
                        tvState.setText("● " + f.unreadCount + " DECRYPTED");
                        tvState.setTextSize(9);
                        tvState.setTextColor(getResources().getColor(R.color.sophisticated_error));
                        tvState.setTypeface(null, android.graphics.Typeface.BOLD);
                        card.addView(tvState);
                    }
                    
                    card.setOnClickListener(v -> selectFriend(f.id));
                    layoutFriendsList.addView(card);
                }
            });
        });
    }

    private void renderKeysInspector() {
        if (selectedFriendId == null) {
            tvPeerFallbackInfo.setVisibility(View.VISIBLE);
            layoutPeerInfoGroup.setVisibility(View.GONE);
        } else {
            tvPeerFallbackInfo.setVisibility(View.GONE);
            layoutPeerInfoGroup.setVisibility(View.VISIBLE);
            
            diskExecutor.execute(() -> {
                Friend buddy = dbHelper.getFriendById(selectedFriendId);
                runOnUiThread(() -> {
                    if (buddy != null) {
                        tvPeerChatWith.setText("SECURE TUNNEL CORNER: " + buddy.name.toUpperCase());
                        tvPeerPubKey.setText(buddy.publicKeyString);
                        tvPeerSessionKey.setText(buddy.sessionAESKeyEncrypted);
                    }
                });
            });
        }

        // Render logs list in container
        layoutAuditLogs.removeAllViews();
        for (String log : auditLogs) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, dpToPx(2), 0, dpToPx(2));
            row.setLayoutParams(rowLp);

            String[] components = log.split(" \\| ");
            String timeline = components[0];
            String action = components.length > 1 ? components[1] : "";

            TextView tvTime = new TextView(this);
            tvTime.setText(timeline);
            tvTime.setTextSize(9);
            tvTime.setTypeface(android.graphics.Typeface.MONOSPACE);
            tvTime.setTextColor(getResources().getColor(R.color.sophisticated_text_muted));
            
            TextView tvAction = new TextView(this);
            tvAction.setText(action);
            tvAction.setTextSize(9);
            tvAction.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            tvAction.setTextColor(getResources().getColor(R.color.sophisticated_primary));
            tvAction.setPadding(dpToPx(16), 0, 0, 0);

            row.addView(tvTime);
            row.addView(tvAction);
            layoutAuditLogs.addView(row);
        }
    }

    private void startBackgroundSimulations() {
        simulationRunnable = new Runnable() {
            @Override
            public void run() {
                if (isSimulatingMovement) {
                    diskExecutor.execute(() -> {
                        // Drift user coordinate location marginally
                        userLat += (random.nextDouble() - 0.5) * 0.0003;
                        userLng += (random.nextDouble() - 0.5) * 0.0003;
                        
                        // Drift friends coordinates location wandering
                        List<Friend> all = dbHelper.getAllFriends();
                        for (Friend friend : all) {
                            double driftLat = friend.latitude + (random.nextDouble() - 0.5) * 0.00045;
                            double driftLng = friend.longitude + (random.nextDouble() - 0.5) * 0.00045;
                            double driftSpeed = Math.max(0.0, Math.min(45.0, friend.speedKmh + (random.nextDouble() - 0.5) * 2.5));
                            int batteryDrain = Math.max(1, friend.batteryPercent - (random.nextInt(100) < 4 ? 1 : 0));
                            
                            dbHelper.updateFriendLocation(friend.id, driftLat, driftLng, driftSpeed, batteryDrain);
                        }
                        
                        refreshFriendsList();
                    });
                }
                simulationHandler.postDelayed(this, 3000);
            }
        };
        simulationHandler.postDelayed(simulationRunnable, 3000);
    }

    private void addAuditLog(String action) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
        String currentStamp = sdf.format(new Date());
        auditLogs.add(0, currentStamp + " | " + action);
        if (auditLogs.size() > 25) {
            auditLogs.remove(auditLogs.size() - 1);
        }
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
        dist = Math.acos(dist);
        dist = Math.toDegrees(dist);
        dist = dist * 60 * 1.1515 * 1.609344; // in Kilometers
        return dist;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        simulationHandler.removeCallbacks(simulationRunnable);
    }
}
