package com.example.gomokutactics.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gomokutactics.R;
import com.example.gomokutactics.online.FirebaseRoomManager;
import com.example.gomokutactics.utils.GamePrefs;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

public class OnlineLobbyActivity extends AppCompatActivity {

    private FirebaseRoomManager roomManager;
    private Button btnCreateRoom, btnJoinRoom;
    private LinearLayout layoutWaitingRoom;
    private TextView tvGeneratedRoomCode, tvRoomConfigSummary;
    private EditText edtRoomCodeInput;

    private String createdRoomCode = null;
    private DatabaseReference currentRoomRef = null;
    private ValueEventListener roomStatusListener = null;
    private boolean navigatedToGame = false;
    private boolean isRequestPending = false;

    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_online_lobby);
        setContentView(R.layout.activity_online_lobby);

        roomManager = new FirebaseRoomManager();

        btnCreateRoom = findViewById(R.id.btnCreateRoom);
        btnJoinRoom = findViewById(R.id.btnJoinRoom);
        layoutWaitingRoom = findViewById(R.id.layoutWaitingRoom);
        tvGeneratedRoomCode = findViewById(R.id.tvGeneratedRoomCode);
        tvRoomConfigSummary = findViewById(R.id.tvRoomConfigSummary);
        edtRoomCodeInput = findViewById(R.id.edtRoomCodeInput);

        int size = GamePrefs.getBoardSize(this);
        int timeMin = GamePrefs.getTimeLimitMinutes(this);
        String timeStr = (timeMin == 0) ? "∞" : (timeMin + "m");
        tvRoomConfigSummary.setText(getString(R.string.online_create_desc) +
                "\n(" + size + "x" + size + " • " + timeStr + ")");

        btnCreateRoom.setOnClickListener(v -> handleCreateRoom());
        btnJoinRoom.setOnClickListener(v -> handleJoinRoom());

        findViewById(R.id.btnShareRoomCode).setOnClickListener(v -> {
            if (createdRoomCode == null) return;
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, getString(R.string.online_share_text, createdRoomCode));
            startActivity(Intent.createChooser(share, getString(R.string.btn_share_code)));
        });

        findViewById(R.id.btnCancelRoom).setOnClickListener(v -> cancelCreatedRoom());
    }

    private boolean isActivityInactive() {
        return isFinishing() || isDestroyed();
    }

    private void showLocalizedError(@StringRes int errorResId, @Nullable String detail) {
        String msg = (detail != null) ? getString(errorResId, detail) : getString(errorResId);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void handleCreateRoom() {
        if (isRequestPending || createdRoomCode != null) return;

        isRequestPending = true;
        btnCreateRoom.setEnabled(false);
        btnJoinRoom.setEnabled(false);
        btnCreateRoom.setText(R.string.online_connecting);

        Runnable timeoutTask = () -> {
            if (isRequestPending && !isActivityInactive()) {
                isRequestPending = false;
                btnCreateRoom.setEnabled(true);
                btnJoinRoom.setEnabled(true);
                btnCreateRoom.setText(R.string.btn_create_room);
                Toast.makeText(this, R.string.online_timeout_create, Toast.LENGTH_LONG).show();
            }
        };
        timeoutHandler.postDelayed(timeoutTask, 8000);

        int boardSize = GamePrefs.getBoardSize(this);
        int timeLimit = GamePrefs.getTimeLimitMinutes(this);
        boolean blockTwoEnds = GamePrefs.isBlockTwoEnds(this);

        roomManager.createRoom(boardSize, timeLimit, blockTwoEnds, new FirebaseRoomManager.CreateRoomCallback() {
            @Override
            public void onRoomCreated(String roomCode, DatabaseReference roomRef) {
                timeoutHandler.removeCallbacks(timeoutTask);
                isRequestPending = false;

                if (isActivityInactive()) {
                    roomRef.removeValue();
                    return;
                }

                btnCreateRoom.setText(R.string.btn_create_room);
                btnJoinRoom.setEnabled(true);

                createdRoomCode = roomCode;
                currentRoomRef = roomRef;
                tvGeneratedRoomCode.setText(roomCode);
                layoutWaitingRoom.setVisibility(View.VISIBLE);

                roomStatusListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (isActivityInactive() || navigatedToGame) return;
                        String status = snapshot.child("status").getValue(String.class);
                        if ("PLAYING".equals(status)) {
                            navigatedToGame = true;
                            launchOnlineMatch(roomCode, true, boardSize, timeLimit, blockTwoEnds);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                };
                roomRef.addValueEventListener(roomStatusListener);
            }

            @Override
            public void onError(@StringRes int errorResId, @Nullable String detail) {
                timeoutHandler.removeCallbacks(timeoutTask);
                isRequestPending = false;
                if (isActivityInactive()) return;

                btnCreateRoom.setEnabled(true);
                btnJoinRoom.setEnabled(true);
                btnCreateRoom.setText(R.string.btn_create_room);
                showLocalizedError(errorResId, detail);
            }
        });
    }

    private void handleJoinRoom() {
        if (isRequestPending) return;

        String code = edtRoomCodeInput.getText().toString().trim();
        if (code.length() != 6) {
            Toast.makeText(this, R.string.online_invalid_code, Toast.LENGTH_SHORT).show();
            return;
        }
        if (code.equals(createdRoomCode)) {
            Toast.makeText(this, R.string.online_self_join_error, Toast.LENGTH_SHORT).show();
            return;
        }

        isRequestPending = true;
        btnJoinRoom.setEnabled(false);
        btnCreateRoom.setEnabled(false);
        btnJoinRoom.setText(R.string.online_searching);

        Runnable timeoutTask = () -> {
            if (isRequestPending && !isActivityInactive()) {
                isRequestPending = false;
                btnJoinRoom.setEnabled(true);
                btnCreateRoom.setEnabled(createdRoomCode == null);
                btnJoinRoom.setText(R.string.btn_join_room);
                Toast.makeText(this, R.string.online_timeout_join, Toast.LENGTH_LONG).show();
            }
        };
        timeoutHandler.postDelayed(timeoutTask, 8000);

        roomManager.joinRoom(code, new FirebaseRoomManager.JoinRoomCallback() {
            @Override
            public void onRoomJoined(String roomCode, int boardSize, int timeLimitMinutes, boolean blockTwoEnds) {
                timeoutHandler.removeCallbacks(timeoutTask);
                isRequestPending = false;
                if (isActivityInactive() || navigatedToGame) return;

                if (currentRoomRef != null) {
                    if (roomStatusListener != null) currentRoomRef.removeEventListener(roomStatusListener);
                    currentRoomRef.removeValue();
                    currentRoomRef = null;
                }

                navigatedToGame = true;
                launchOnlineMatch(roomCode, false, boardSize, timeLimitMinutes, blockTwoEnds);
            }

            @Override
            public void onError(@StringRes int errorResId, @Nullable String detail) {
                timeoutHandler.removeCallbacks(timeoutTask);
                isRequestPending = false;
                if (isActivityInactive()) return;

                btnJoinRoom.setEnabled(true);
                btnCreateRoom.setEnabled(createdRoomCode == null);
                btnJoinRoom.setText(R.string.btn_join_room);
                showLocalizedError(errorResId, detail);
            }
        });
    }

    private void launchOnlineMatch(String roomCode, boolean isHost, int boardSize, int timeLimitMinutes, boolean blockTwoEnds) {
        if (currentRoomRef != null && roomStatusListener != null) {
            currentRoomRef.removeEventListener(roomStatusListener);
            roomStatusListener = null;
        }
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra("GAME_MODE", "ONLINE");
        intent.putExtra("ROOM_CODE", roomCode);
        intent.putExtra("IS_HOST", isHost);
        intent.putExtra("BOARD_SIZE", boardSize);
        intent.putExtra("TIME_LIMIT_MINUTES", timeLimitMinutes);
        intent.putExtra("BLOCK_TWO_ENDS", blockTwoEnds);
        startActivity(intent);
        finish();
    }

    private void cancelCreatedRoom() {
        if (currentRoomRef != null) {
            if (roomStatusListener != null) {
                currentRoomRef.removeEventListener(roomStatusListener);
                roomStatusListener = null;
            }
            currentRoomRef.removeValue();
            currentRoomRef = null;
        }
        createdRoomCode = null;
        layoutWaitingRoom.setVisibility(View.GONE);
        btnCreateRoom.setEnabled(true);
        btnCreateRoom.setText(R.string.btn_create_room);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeoutHandler.removeCallbacksAndMessages(null);
        if (!navigatedToGame && currentRoomRef != null) {
            if (roomStatusListener != null) {
                currentRoomRef.removeEventListener(roomStatusListener);
                roomStatusListener = null;
            }
            currentRoomRef.removeValue();
            currentRoomRef = null;
        }
    }
}