package com.example.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "nexus_share.db";
    private static final int DATABASE_VERSION = 1;

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE friends (" +
                "id TEXT PRIMARY KEY, " +
                "name TEXT, " +
                "latitude REAL, " +
                "longitude REAL, " +
                "speedKmh REAL, " +
                "batteryPercent INTEGER, " +
                "publicKeyString TEXT, " +
                "sessionAESKeyEncrypted TEXT, " +
                "trackingActive INTEGER, " +
                "unreadCount INTEGER)");

        db.execSQL("CREATE TABLE messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "friendId TEXT, " +
                "isFromUser INTEGER, " +
                "cipherText TEXT, " +
                "iv TEXT, " +
                "timestamp INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS friends");
        db.execSQL("DROP TABLE IF EXISTS messages");
        onCreate(db);
    }

    // Friend operations
    public synchronized List<Friend> getAllFriends() {
        List<Friend> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM friends", null);
        try {
            if (cursor.moveToFirst()) {
                do {
                    list.add(new Friend(
                        cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                        cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")),
                        cursor.getDouble(cursor.getColumnIndexOrThrow("speedKmh")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("batteryPercent")),
                        cursor.getString(cursor.getColumnIndexOrThrow("publicKeyString")),
                        cursor.getString(cursor.getColumnIndexOrThrow("sessionAESKeyEncrypted")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("trackingActive")) == 1,
                        cursor.getInt(cursor.getColumnIndexOrThrow("unreadCount"))
                    ));
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    public synchronized Friend getFriendById(String friendId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM friends WHERE id = ? LIMIT 1", new String[]{friendId});
        try {
            if (cursor.moveToFirst()) {
                return new Friend(
                    cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                    cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")),
                    cursor.getDouble(cursor.getColumnIndexOrThrow("speedKmh")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("batteryPercent")),
                    cursor.getString(cursor.getColumnIndexOrThrow("publicKeyString")),
                    cursor.getString(cursor.getColumnIndexOrThrow("sessionAESKeyEncrypted")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("trackingActive")) == 1,
                    cursor.getInt(cursor.getColumnIndexOrThrow("unreadCount"))
                );
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    public synchronized void insertFriends(List<Friend> friends) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Friend f : friends) {
                ContentValues cv = new ContentValues();
                cv.put("id", f.id);
                cv.put("name", f.name);
                cv.put("latitude", f.latitude);
                cv.put("longitude", f.longitude);
                cv.put("speedKmh", f.speedKmh);
                cv.put("batteryPercent", f.batteryPercent);
                cv.put("publicKeyString", f.publicKeyString);
                cv.put("sessionAESKeyEncrypted", f.sessionAESKeyEncrypted);
                cv.put("trackingActive", f.trackingActive ? 1 : 0);
                cv.put("unreadCount", f.unreadCount);
                db.insertWithOnConflict("friends", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void updateFriend(Friend f) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", f.name);
        cv.put("latitude", f.latitude);
        cv.put("longitude", f.longitude);
        cv.put("speedKmh", f.speedKmh);
        cv.put("batteryPercent", f.batteryPercent);
        cv.put("publicKeyString", f.publicKeyString);
        cv.put("sessionAESKeyEncrypted", f.sessionAESKeyEncrypted);
        cv.put("trackingActive", f.trackingActive ? 1 : 0);
        cv.put("unreadCount", f.unreadCount);
        db.update("friends", cv, "id = ?", new String[]{f.id});
    }

    public synchronized void updateFriendLocation(String friendId, double lat, double lng, double speed, int battery) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("latitude", lat);
        cv.put("longitude", lng);
        cv.put("speedKmh", speed);
        cv.put("batteryPercent", battery);
        db.update("friends", cv, "id = ?", new String[]{friendId});
    }

    public synchronized void incrementUnreadCount(String friendId) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE friends SET unreadCount = unreadCount + 1 WHERE id = ?", new Object[]{friendId});
    }

    public synchronized void clearUnreadCount(String friendId) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE friends SET unreadCount = 0 WHERE id = ?", new Object[]{friendId});
    }

    // Message operations
    public synchronized List<Message> getMessagesForFriend(String friendId) {
        List<Message> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM messages WHERE friendId = ? ORDER BY timestamp ASC", new String[]{friendId});
        try {
            if (cursor.moveToFirst()) {
                do {
                    list.add(new Message(
                        cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("friendId")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("isFromUser")) == 1,
                        cursor.getString(cursor.getColumnIndexOrThrow("cipherText")),
                        cursor.getString(cursor.getColumnIndexOrThrow("iv")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                    ));
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    public synchronized void insertMessage(Message m) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("friendId", m.friendId);
        cv.put("isFromUser", m.isFromUser ? 1 : 0);
        cv.put("cipherText", m.cipherText);
        cv.put("iv", m.iv);
        cv.put("timestamp", m.timestamp);
        db.insert("messages", null, cv);
    }

    public synchronized void deleteMessagesForFriend(String friendId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("messages", "friendId = ?", new String[]{friendId});
    }
}
