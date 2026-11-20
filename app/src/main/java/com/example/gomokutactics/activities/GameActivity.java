package com.example.gomokutactics.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.gomokutactics.R;
import com.example.gomokutactics.ai.GomokuAI;
import com.example.gomokutactics.database.MatchDatabaseHelper;
import com.example.gomokutactics.models.Move;
import com.example.gomokutactics.online.FirebaseRoomManager;
import com.example.gomokutactics.utils.GamePrefs;
import com.example.gomokutactics.utils.SoundManager;
import com.example.gomokutactics.views.GomokuBoardView;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameActivity extends AppCompatActivity {

    private GomokuBoardView boardView;
    private TextView tvGameModeBadge, tvBoardSizeBadge, tvTurnIndicator, tvAiThinking;
    private TextView tvP1Label, tvP2Label, tvP1Clock, tvP2Clock;
    private MaterialCardView cardClockP1, cardClockP2;
    private Button btnHint, btnUndo, btnRestart;

    private String gameMode = "AI"; // "AI", "LOCAL", "ONLINE"
    private int boardSize = 15;
    private int timeLimitMinutes = 5;
    private int difficulty = 2;
    private boolean blockTwoEnds = false;

    private String roomCode = "";
    private boolean isHost = true;
    private int myOnlinePlayer = 1;
    private DatabaseReference roomRef;
    private ValueEventListener roomListener;
    private int syncedStepCount = 0;

    private int currentPlayer = 1;
    private boolean gameOver = false;
    private boolean matchSaved = false;
    private boolean isAiThinking = false;
    private int matchGeneration = 0;
    private int hintsRemaining = 3;

    private long p1TimeLeftMs = 0;
    private long p2TimeLeftMs = 0;
    private CountDownTimer activeClockTimer;

    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private MatchDatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_game);
        setContentView(R.layout.activity_game);

        dbHelper = new MatchDatabaseHelper(this);

        gameMode = getIntent().getStringExtra("GAME_MODE");
        if (gameMode == null) gameMode = GamePrefs.isVsAI(this) ? "AI" : "LOCAL";
        boardSize = getIntent().getIntExtra("BOARD_SIZE", GamePrefs.getBoardSize(this));
        timeLimitMinutes = getIntent().getIntExtra("TIME_LIMIT_MINUTES", GamePrefs.getTimeLimitMinutes(this));
        difficulty = getIntent().getIntExtra("DIFFICULTY", GamePrefs.getDifficulty(this));
        blockTwoEnds = getIntent().getBooleanExtra("BLOCK_TWO_ENDS", GamePrefs.isBlockTwoEnds(this));

        roomCode = getIntent().getStringExtra("ROOM_CODE");
        isHost = getIntent().getBooleanExtra("IS_HOST", true);
        myOnlinePlayer = isHost ? 1 : 2;

        bindViews();
        initMatch();

        if ("ONLINE".equals(gameMode) && roomCode != null) {
            setupFirebaseRoomSync();
        }

        boardView.setOnCellClickListener((row, col) -> {
            if (gameOver || isAiThinking) return;
            if ("AI".equals(gameMode) && currentPlayer == 2) return;
            if ("ONLINE".equals(gameMode) && currentPlayer != myOnlinePlayer) return;

            handleLocalMove(row, col);
        });

        btnHint.setOnClickListener(v -> requestHintMove());

        btnUndo.setOnClickListener(v -> {
            if (gameOver || isAiThinking || "ONLINE".equals(gameMode)) return;
            if ("AI".equals(gameMode)) {
                if (currentPlayer == 1 && boardView.getMoveHistory().size() >= 2) {
                    boardView.undoLastMoves(2);
                    startPlayerClock();
                } else {
                    Toast.makeText(this, R.string.toast_not_enough_undo, Toast.LENGTH_SHORT).show();
                }
            } else {
                if (!boardView.getMoveHistory().isEmpty()) {
                    boardView.undoLastMoves(1);
                    currentPlayer = (currentPlayer == 1) ? 2 : 1;
                    updateTurnUI();
                    startPlayerClock();
                }
            }
        });

        btnRestart.setOnClickListener(v -> {
            if (!"ONLINE".equals(gameMode)) {
                initMatch();
            }
        });
    }

    private void bindViews() {
        boardView = findViewById(R.id.gomokuBoardView);
        tvGameModeBadge = findViewById(R.id.tvGameModeBadge);
        tvBoardSizeBadge = findViewById(R.id.tvBoardSizeBadge);
        tvTurnIndicator = findViewById(R.id.tvTurnIndicator);
        tvAiThinking = findViewById(R.id.tvAiThinking);
        tvP1Label = findViewById(R.id.tvP1Label);
        tvP2Label = findViewById(R.id.tvP2Label);
        tvP1Clock = findViewById(R.id.tvP1Clock);
        tvP2Clock = findViewById(R.id.tvP2Clock);
        cardClockP1 = findViewById(R.id.cardClockP1);
        cardClockP2 = findViewById(R.id.cardClockP2);
        btnHint = findViewById(R.id.btnHint);
        btnUndo = findViewById(R.id.btnUndo);
        btnRestart = findViewById(R.id.btnRestart);
    }

    private void initMatch() {
        matchGeneration++;
        gameOver = false;
        matchSaved = false;
        isAiThinking = false;
        currentPlayer = 1;
        hintsRemaining = 3;
        syncedStepCount = 0;

        tvAiThinking.setVisibility(View.GONE);
        p1TimeLeftMs = timeLimitMinutes * 60_000L;
        p2TimeLeftMs = timeLimitMinutes * 60_000L;

        boardView.applyThemePaints();
        boardView.setBoardSize(boardSize);
        boardView.setInteractive(true);

        tvBoardSizeBadge.setText(boardSize + "x" + boardSize);
        setupRoleLabelsAndButtons();
        updateClockTexts();
        updateTurnUI();
        startPlayerClock();
    }

    private String getLocalizedDifficultyLabel() {
        if (difficulty == 1) return getString(R.string.diff_easy);
        if (difficulty == 2) return getString(R.string.diff_hard);
        return getString(R.string.diff_extreme);
    }

    private void setupRoleLabelsAndButtons() {
        if ("AI".equals(gameMode)) {
            tvGameModeBadge.setText(getString(R.string.game_ai_mode_badge, getLocalizedDifficultyLabel()));
            tvP1Label.setText(R.string.player_x_you);
            tvP2Label.setText(R.string.player_o_ai);
            btnHint.setVisibility(View.VISIBLE);
            btnUndo.setVisibility(View.VISIBLE);
            btnRestart.setVisibility(View.VISIBLE);
            updateHintButtonUI();
        } else if ("LOCAL".equals(gameMode)) {
            tvGameModeBadge.setText(R.string.mode_2_players);
            tvP1Label.setText(R.string.player_x_p1);
            tvP2Label.setText(R.string.player_o_p2);
            btnHint.setVisibility(View.GONE);
            btnUndo.setVisibility(View.VISIBLE);
            btnRestart.setVisibility(View.VISIBLE);
        } else {
            tvGameModeBadge.setText(getString(R.string.game_online_mode_badge, roomCode));
            tvP1Label.setText(isHost ? R.string.player_x_you : R.string.player_x_host);
            tvP2Label.setText(!isHost ? R.string.player_o_you : R.string.player_o_guest);
            btnHint.setVisibility(View.GONE);
            btnUndo.setVisibility(View.GONE);
            btnRestart.setVisibility(View.GONE);
        }
    }

    private void updateHintButtonUI() {
        btnHint.setText(getString(R.string.btn_hint, hintsRemaining));
        btnHint.setEnabled(hintsRemaining > 0 && !gameOver && !isAiThinking);
    }

    private void updateTurnUI() {
        int activeStroke = ContextCompat.getColor(this, R.color.win_green);
        cardClockP1.setStrokeWidth(currentPlayer == 1 ? 5 : 0);
        cardClockP1.setStrokeColor(activeStroke);
        cardClockP2.setStrokeWidth(currentPlayer == 2 ? 5 : 0);
        cardClockP2.setStrokeColor(activeStroke);

        if ("AI".equals(gameMode)) {
            tvTurnIndicator.setText(currentPlayer == 1 ? R.string.turn_your : R.string.turn_ai);
        } else if ("LOCAL".equals(gameMode)) {
            tvTurnIndicator.setText(currentPlayer == 1 ? R.string.turn_p1 : R.string.turn_p2);
        } else {
            boolean isMyTurn = (currentPlayer == myOnlinePlayer);
            tvTurnIndicator.setText(isMyTurn ? R.string.turn_your : R.string.turn_opponent_online);
        }
    }

    private void startPlayerClock() {
        if (activeClockTimer != null) activeClockTimer.cancel();
        if (gameOver || timeLimitMinutes <= 0) {
            updateClockTexts();
            return;
        }

        final int tickingPlayer = currentPlayer;
        long remainingForCurrent = (tickingPlayer == 1) ? p1TimeLeftMs : p2TimeLeftMs;
        if (remainingForCurrent <= 0) {
            if (!"ONLINE".equals(gameMode) || tickingPlayer == myOnlinePlayer) {
                handleTimeoutLoss(tickingPlayer);
            }
            return;
        }

        activeClockTimer = new CountDownTimer(remainingForCurrent, 250) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (gameOver || currentPlayer != tickingPlayer) return;
                if (tickingPlayer == 1) {
                    p1TimeLeftMs = millisUntilFinished;
                } else {
                    p2TimeLeftMs = millisUntilFinished;
                }
                updateClockTexts();
            }

            @Override
            public void onFinish() {
                if (gameOver || currentPlayer != tickingPlayer) return;
                if (tickingPlayer == 1) p1TimeLeftMs = 0;
                else p2TimeLeftMs = 0;
                updateClockTexts();

                if (!"ONLINE".equals(gameMode) || tickingPlayer == myOnlinePlayer) {
                    handleTimeoutLoss(tickingPlayer);
                }
            }
        }.start();
    }

    private void updateClockTexts() {
        if (timeLimitMinutes <= 0) {
            tvP1Clock.setText("∞");
            tvP2Clock.setText("∞");
            return;
        }
        tvP1Clock.setText(formatMs(p1TimeLeftMs));
        tvP2Clock.setText(formatMs(p2TimeLeftMs));
    }

    private String formatMs(long ms) {
        long totalSec = Math.max(0, ms / 1000);
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format(Locale.US, "%02d:%02d", min, sec);
    }

    private void handleTimeoutLoss(int timedOutPlayer) {
        if (gameOver) return;
        gameOver = true;
        if (activeClockTimer != null) activeClockTimer.cancel();
        boardView.setInteractive(false);
        SoundManager.playWinSound(this);

        int winner = (timedOutPlayer == 1) ? 2 : 1;

        if ("ONLINE".equals(gameMode) && roomRef != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "FINISHED");
            updates.put("winner", winner);
            updates.put("endReason", "TIMEOUT");
            roomRef.updateChildren(updates);
        }

        announceWinner(winner, true);
    }

    private void handleLocalMove(int row, int col) {
        if (!boardView.placePiece(row, col, currentPlayer)) return;
        SoundManager.playPlaceSound(this);

        if ("ONLINE".equals(gameMode) && roomRef != null) {
            syncedStepCount++;
            long myRemaining = (myOnlinePlayer == 1) ? p1TimeLeftMs : p2TimeLeftMs;
            Map<String, Object> updates = new HashMap<>();
            updates.put("lastRow", row);
            updates.put("lastCol", col);
            updates.put("lastPlayer", myOnlinePlayer);
            updates.put("moveStep", syncedStepCount);
            updates.put("senderTimeLeftMs", myRemaining);
            roomRef.updateChildren(updates);
        }

        if (checkFiveInRowEnd(row, col, currentPlayer)) return;

        currentPlayer = (currentPlayer == 1) ? 2 : 1;
        updateTurnUI();
        startPlayerClock();

        if ("AI".equals(gameMode) && currentPlayer == 2) {
            triggerAIMove();
        }
    }

    private void setupFirebaseRoomSync() {
        roomRef = new FirebaseRoomManager().getRoomRef(roomCode);
        roomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (gameOver || isFinishing() || isDestroyed() || !snapshot.exists()) return;

                String status = snapshot.child("status").getValue(String.class);
                if ("ABANDONED".equals(status)) {
                    gameOver = true;
                    if (activeClockTimer != null) activeClockTimer.cancel();
                    boardView.setInteractive(false);
                    Toast.makeText(GameActivity.this, R.string.toast_opponent_left, Toast.LENGTH_LONG).show();
                    announceWinner(myOnlinePlayer, false);
                    return;
                }

                if ("FINISHED".equals(status)) {
                    Integer winner = snapshot.child("winner").getValue(Integer.class);
                    String reason = snapshot.child("endReason").getValue(String.class);
                    if (winner != null && winner > 0) {
                        gameOver = true;
                        if (activeClockTimer != null) activeClockTimer.cancel();
                        boardView.setInteractive(false);
                        announceWinner(winner, "TIMEOUT".equals(reason));
                        return;
                    }
                }

                Integer step = snapshot.child("moveStep").getValue(Integer.class);
                Integer lastPlayer = snapshot.child("lastPlayer").getValue(Integer.class);
                Integer lastRow = snapshot.child("lastRow").getValue(Integer.class);
                Integer lastCol = snapshot.child("lastCol").getValue(Integer.class);
                Long senderTime = snapshot.child("senderTimeLeftMs").getValue(Long.class);

                if (step != null && step > syncedStepCount && lastPlayer != null && lastPlayer != myOnlinePlayer) {
                    syncedStepCount = step;
                    if (senderTime != null && timeLimitMinutes > 0) {
                        if (lastPlayer == 1) p1TimeLeftMs = senderTime;
                        else p2TimeLeftMs = senderTime;
                        updateClockTexts();
                    }

                    if (lastRow != null && lastCol != null && lastRow >= 0 && lastCol >= 0) {
                        if (boardView.placePiece(lastRow, lastCol, lastPlayer)) {
                            SoundManager.playPlaceSound(GameActivity.this);

                            if (!checkFiveInRowEnd(lastRow, lastCol, lastPlayer)) {
                                currentPlayer = myOnlinePlayer;
                                updateTurnUI();
                                startPlayerClock();
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        roomRef.addValueEventListener(roomListener);
    }

    private void triggerAIMove() {
        final int currentGen = matchGeneration;
        isAiThinking = true;
        boardView.setInteractive(false);
        btnUndo.setEnabled(false);
        updateHintButtonUI();
        tvAiThinking.setVisibility(View.VISIBLE);

        final int[][] boardCopy = copyBoard(boardView.getBoard(), boardSize);
        final int targetSize = boardSize;
        final int targetDiff = difficulty;
        final boolean targetRule = blockTwoEnds;

        aiExecutor.execute(() -> {
            Move aiMove = GomokuAI.findBestMove(boardCopy, targetSize, 2, targetDiff, targetRule);

            runOnUiThread(() -> {
                if (currentGen != matchGeneration || gameOver || isFinishing() || isDestroyed()) {
                    return;
                }
                isAiThinking = false;
                tvAiThinking.setVisibility(View.GONE);
                boardView.setInteractive(true);
                btnUndo.setEnabled(true);

                boardView.placePiece(aiMove.row, aiMove.col, 2);
                SoundManager.playPlaceSound(this);

                if (!checkFiveInRowEnd(aiMove.row, aiMove.col, 2)) {
                    currentPlayer = 1;
                    updateTurnUI();
                    updateHintButtonUI();
                    startPlayerClock();
                } else {
                    updateHintButtonUI();
                }
            });
        });
    }

    private void requestHintMove() {
        if (!"AI".equals(gameMode) || gameOver || isAiThinking || currentPlayer != 1 || hintsRemaining <= 0) return;

        final int currentGen = matchGeneration;
        hintsRemaining--;
        isAiThinking = true;
        boardView.setInteractive(false);
        btnUndo.setEnabled(false);
        updateHintButtonUI();
        tvAiThinking.setVisibility(View.VISIBLE);

        final int[][] boardCopy = copyBoard(boardView.getBoard(), boardSize);
        final int targetSize = boardSize;
        final boolean targetRule = blockTwoEnds;

        aiExecutor.execute(() -> {
            Move hint = GomokuAI.getHintMove(boardCopy, targetSize, 1, targetRule);

            runOnUiThread(() -> {
                if (currentGen != matchGeneration || gameOver || isFinishing() || isDestroyed()) {
                    return;
                }
                isAiThinking = false;
                tvAiThinking.setVisibility(View.GONE);
                boardView.setInteractive(true);
                btnUndo.setEnabled(true);
                updateHintButtonUI();

                boardView.setHintMove(hint);
                SoundManager.playHintSound(this);
            });
        });
    }

    private boolean checkFiveInRowEnd(int lastRow, int lastCol, int player) {
        List<Move> winLine = findWinningLine(boardView.getBoard(), boardSize, lastRow, lastCol, player, blockTwoEnds);
        if (winLine != null) {
            gameOver = true;
            if (activeClockTimer != null) activeClockTimer.cancel();
            boardView.setInteractive(false);
            boardView.setWinningLine(winLine);
            if ("AI".equals(gameMode)) updateHintButtonUI();
            SoundManager.playWinSound(this);

            if ("ONLINE".equals(gameMode) && roomRef != null) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", "FINISHED");
                updates.put("winner", player);
                updates.put("endReason", "FIVE_IN_ROW");
                roomRef.updateChildren(updates);
            }

            announceWinner(player, false);
            return true;
        }

        if (boardView.getMoveHistory().size() >= boardSize * boardSize) {
            gameOver = true;
            if (activeClockTimer != null) activeClockTimer.cancel();
            boardView.setInteractive(false);
            saveMatchToDatabase("HÒA");
            showEndGameDialog(getString(R.string.dialog_draw_title), "HÒA");
            return true;
        }
        return false;
    }

    private void announceWinner(int winnerPlayer, boolean byTimeout) {
        String canonicalResultCode;
        String dialogTitle;
        String suffix = byTimeout ? getString(R.string.suffix_timeout) : "";

        if ("AI".equals(gameMode)) {
            canonicalResultCode = (winnerPlayer == 1) ? "THẮNG" : "THUA";
            dialogTitle = (winnerPlayer == 1)
                    ? (getString(R.string.dialog_you_win_title) + suffix)
                    : (getString(R.string.dialog_ai_win_title) + suffix);
        } else if ("ONLINE".equals(gameMode)) {
            boolean iWon = (winnerPlayer == myOnlinePlayer);
            canonicalResultCode = iWon ? "THẮNG" : "THUA";
            dialogTitle = iWon
                    ? (getString(R.string.dialog_online_win_title) + suffix)
                    : (getString(R.string.dialog_online_loss_title) + suffix);
        } else {
            canonicalResultCode = (winnerPlayer == 1) ? "P1 THẮNG" : "P2 THẮNG";
            dialogTitle = (winnerPlayer == 1)
                    ? (getString(R.string.dialog_p1_win_title) + suffix)
                    : (getString(R.string.dialog_p2_win_title) + suffix);
        }

        saveMatchToDatabase(canonicalResultCode);
        showEndGameDialog(dialogTitle, canonicalResultCode);
    }

    private void saveMatchToDatabase(String canonicalResultCode) {
        if (matchSaved) return;
        matchSaved = true;

        String canonicalModeStr;
        if ("AI".equals(gameMode)) {
            String diffCanonical = (difficulty == 1) ? "Dễ" : (difficulty == 2 ? "Khó" : "Cực hạn");
            canonicalModeStr = "AI (" + diffCanonical + ")";
        } else if ("ONLINE".equals(gameMode)) {
            canonicalModeStr = "Online #" + roomCode;
        } else {
            canonicalModeStr = "2 Người chơi";
        }

        StringBuilder sb = new StringBuilder();
        List<Move> moves = boardView.getMoveHistory();
        for (int i = 0; i < moves.size(); i++) {
            Move m = moves.get(i);
            sb.append(m.row).append(",").append(m.col).append(",").append(m.player);
            if (i < moves.size() - 1) sb.append(";");
        }

        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        dbHelper.insertMatch(canonicalModeStr, boardSize, canonicalResultCode, moves.size(), now, sb.toString());
    }

    private void showEndGameDialog(String title, String canonicalResultCode) {
        if (isFinishing() || isDestroyed()) return;
        String localizedResult = GamePrefs.getLocalizedResult(this, canonicalResultCode);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(getString(R.string.game_end_dialog_msg, boardSize, boardView.getMoveHistory().size()))
                .setCancelable(false)
                .setNeutralButton(R.string.btn_share_result, (d, w) -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, getString(
                            R.string.game_share_result_text, localizedResult, boardSize));
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.chooser_share_via)));
                })
                .setNegativeButton(R.string.btn_back_home, (d, w) -> finish());

        if (!"ONLINE".equals(gameMode)) {
            builder.setPositiveButton(R.string.btn_play_again, (d, w) -> initMatch());
        }
        builder.show();
    }

    private List<Move> findWinningLine(int[][] board, int size, int r, int c, int player, boolean blockTwoEnds) {
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        int opponent = (player == 1) ? 2 : 1;

        for (int[] dir : dirs) {
            List<Move> line = new ArrayList<>();
            line.add(new Move(r, c, player));
            int blockedEnds = 0;

            int nr = r + dir[0], nc = c + dir[1];
            while (nr >= 0 && nr < size && nc >= 0 && nc < size && board[nr][nc] == player) {
                line.add(new Move(nr, nc, player));
                nr += dir[0];
                nc += dir[1];
            }
            if (nr < 0 || nr >= size || nc < 0 || nc >= size || board[nr][nc] == opponent) blockedEnds++;

            nr = r - dir[0];
            nc = c - dir[1];
            while (nr >= 0 && nr < size && nc >= 0 && nc < size && board[nr][nc] == player) {
                line.add(0, new Move(nr, nc, player));
                nr -= dir[0];
                nc -= dir[1];
            }
            if (nr < 0 || nr >= size || nc < 0 || nc >= size || board[nr][nc] == opponent) blockedEnds++;

            if (line.size() >= 5 && (!blockTwoEnds || blockedEnds < 2)) {
                return line;
            }
        }
        return null;
    }

    private int[][] copyBoard(int[][] src, int size) {
        int[][] dst = new int[size][size];
        for (int i = 0; i < size; i++) {
            System.arraycopy(src[i], 0, dst[i], 0, size);
        }
        return dst;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_game, menu);
        if (!"AI".equals(gameMode)) {
            menu.findItem(R.id.game_sub_diff).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.g_diff_easy) {
            difficulty = 1;
            setupRoleLabelsAndButtons();
            return true;
        } else if (id == R.id.g_diff_hard) {
            difficulty = 2;
            setupRoleLabelsAndButtons();
            return true;
        } else if (id == R.id.g_diff_extreme) {
            difficulty = 3;
            setupRoleLabelsAndButtons();
            return true;
        } else if (id == R.id.game_toggle_sound) {
            boolean next = !GamePrefs.isSoundEnabled(this);
            GamePrefs.setSoundEnabled(this, next);
            return true;
        } else if (id == R.id.game_surrender) {
            if (!gameOver) {
                gameOver = true;
                if (activeClockTimer != null) activeClockTimer.cancel();

                if ("ONLINE".equals(gameMode)) {
                    int winner = (myOnlinePlayer == 1) ? 2 : 1;
                    if (roomRef != null) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("status", "FINISHED");
                        updates.put("winner", winner);
                        updates.put("endReason", "SURRENDER");
                        roomRef.updateChildren(updates);
                    }
                    saveMatchToDatabase("THUA");
                } else if ("LOCAL".equals(gameMode)) {
                    saveMatchToDatabase(currentPlayer == 1 ? "P2 THẮNG" : "P1 THẮNG");
                } else {
                    saveMatchToDatabase("THUA");
                }
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activeClockTimer != null) {
            activeClockTimer.cancel();
            activeClockTimer = null;
        }
        if (roomRef != null) {
            if (roomListener != null) {
                roomRef.removeEventListener(roomListener);
                roomListener = null;
            }
            if (!gameOver) {
                roomRef.child("status").setValue("ABANDONED");
            }
        }
        aiExecutor.shutdownNow();
    }
}