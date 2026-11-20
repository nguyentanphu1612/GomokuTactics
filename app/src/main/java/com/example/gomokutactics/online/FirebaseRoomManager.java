package com.example.gomokutactics.online;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.gomokutactics.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Quản lý phòng đấu Online qua Firebase Realtime Database.
 * Trả về mã tài nguyên @StringRes để tự động đồng bộ đa ngôn ngữ Việt - Anh.
 */
public class FirebaseRoomManager {

    private static final String FIREBASE_DB_URL = "https://gokumotatics-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private static final String ROOMS_NODE = "gomoku_rooms";
    private static final int MAX_CREATE_RETRIES = 5;

    private final DatabaseReference roomsRef;
    private final Random random = new Random();

    public interface CreateRoomCallback {
        void onRoomCreated(String roomCode, DatabaseReference roomRef);
        void onError(@StringRes int errorResId, @Nullable String detail);
    }

    public interface JoinRoomCallback {
        void onRoomJoined(String roomCode, int boardSize, int timeLimitMinutes, boolean blockTwoEnds);
        void onError(@StringRes int errorResId, @Nullable String detail);
    }

    public FirebaseRoomManager() {
        FirebaseDatabase database;
        if (!FIREBASE_DB_URL.isEmpty()) {
            database = FirebaseDatabase.getInstance(FIREBASE_DB_URL);
        } else {
            database = FirebaseDatabase.getInstance();
        }
        roomsRef = database.getReference(ROOMS_NODE);
    }

    public void createRoom(int boardSize, int timeLimitMinutes, boolean blockTwoEnds, CreateRoomCallback callback) {
        attemptCreateRoom(boardSize, timeLimitMinutes, blockTwoEnds, 0, callback);
    }

    private void attemptCreateRoom(int boardSize, int timeLimitMinutes, boolean blockTwoEnds, int attempt, CreateRoomCallback callback) {
        if (attempt >= MAX_CREATE_RETRIES) {
            callback.onError(R.string.err_room_create_failed, null);
            return;
        }

        String code = String.valueOf(100000 + random.nextInt(900000));
        DatabaseReference targetRef = roomsRef.child(code);

        targetRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Object existing = currentData.getValue();
                if (existing != null) {
                    Object statusObj = currentData.child("status").getValue();
                    String existingStatus = (statusObj != null) ? String.valueOf(statusObj) : "";
                    if ("WAITING".equals(existingStatus) || "PLAYING".equals(existingStatus)) {
                        return Transaction.abort();
                    }
                }

                Map<String, Object> roomData = new HashMap<>();
                roomData.put("roomCode", code);
                roomData.put("boardSize", boardSize);
                roomData.put("timeLimitMinutes", timeLimitMinutes);
                roomData.put("blockTwoEnds", blockTwoEnds);
                roomData.put("status", "WAITING");
                roomData.put("moveStep", 0);
                roomData.put("lastRow", -1);
                roomData.put("lastCol", -1);
                roomData.put("lastPlayer", 0);
                roomData.put("senderTimeLeftMs", timeLimitMinutes * 60_000L);
                roomData.put("endReason", "");
                roomData.put("winner", 0);

                currentData.setValue(roomData);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                if (error != null) {
                    callback.onError(R.string.firebase_error_prefix, error.getMessage());
                    return;
                }
                if (!committed) {
                    attemptCreateRoom(boardSize, timeLimitMinutes, blockTwoEnds, attempt + 1, callback);
                    return;
                }
                targetRef.child("status").onDisconnect().setValue("ABANDONED");
                callback.onRoomCreated(code, targetRef);
            }
        });
    }

    public void joinRoom(String roomCode, JoinRoomCallback callback) {
        DatabaseReference targetRef = roomsRef.child(roomCode);

        targetRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (currentData.getValue() == null) {
                    return Transaction.abort();
                }
                Object statusObj = currentData.child("status").getValue();
                String status = (statusObj != null) ? String.valueOf(statusObj) : "";
                if (!"WAITING".equals(status)) {
                    return Transaction.abort();
                }

                currentData.child("status").setValue("PLAYING");
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot snapshot) {
                if (error != null) {
                    callback.onError(R.string.firebase_error_prefix, error.getMessage());
                    return;
                }
                if (!committed) {
                    if (snapshot == null || !snapshot.exists()) {
                        callback.onError(R.string.err_room_not_found, null);
                    } else {
                        callback.onError(R.string.err_room_unavailable, null);
                    }
                    return;
                }

                Integer boardSize = (snapshot != null) ? snapshot.child("boardSize").getValue(Integer.class) : null;
                Integer timeLimit = (snapshot != null) ? snapshot.child("timeLimitMinutes").getValue(Integer.class) : null;
                Boolean blockTwo = (snapshot != null) ? snapshot.child("blockTwoEnds").getValue(Boolean.class) : null;

                int finalSize = (boardSize != null) ? boardSize : 15;
                int finalTime = (timeLimit != null) ? timeLimit : 5;
                boolean finalBlock = (blockTwo != null) && blockTwo;

                targetRef.child("status").onDisconnect().setValue("ABANDONED");
                callback.onRoomJoined(roomCode, finalSize, finalTime, finalBlock);
            }
        });
    }

    public DatabaseReference getRoomRef(String roomCode) {
        return roomsRef.child(roomCode);
    }
}