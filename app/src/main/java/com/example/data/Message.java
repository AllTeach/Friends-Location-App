package com.example.data;

public class Message {
    public int id;
    public String friendId;
    public boolean isFromUser;
    public String cipherText;
    public String iv;
    public long timestamp;

    public Message(int id, String friendId, boolean isFromUser, String cipherText, String iv, long timestamp) {
        this.id = id;
        this.friendId = friendId;
        this.isFromUser = isFromUser;
        this.cipherText = cipherText;
        this.iv = iv;
        this.timestamp = timestamp;
    }

    public Message(String friendId, boolean isFromUser, String cipherText, String iv) {
        this.id = 0;
        this.friendId = friendId;
        this.isFromUser = isFromUser;
        this.cipherText = cipherText;
        this.iv = iv;
        this.timestamp = System.currentTimeMillis();
    }
}
