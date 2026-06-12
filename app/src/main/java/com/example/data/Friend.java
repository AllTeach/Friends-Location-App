package com.example.data;

public class Friend {
    public String id;
    public String name;
    public double latitude;
    public double longitude;
    public double speedKmh;
    public int batteryPercent;
    public String publicKeyString;
    public String sessionAESKeyEncrypted;
    public boolean trackingActive;
    public int unreadCount;

    public Friend(String id, String name, double latitude, double longitude, double speedKmh, 
                  int batteryPercent, String publicKeyString, String sessionAESKeyEncrypted, 
                  boolean trackingActive, int unreadCount) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speedKmh = speedKmh;
        this.batteryPercent = batteryPercent;
        this.publicKeyString = publicKeyString;
        this.sessionAESKeyEncrypted = sessionAESKeyEncrypted;
        this.trackingActive = trackingActive;
        this.unreadCount = unreadCount;
    }
}
