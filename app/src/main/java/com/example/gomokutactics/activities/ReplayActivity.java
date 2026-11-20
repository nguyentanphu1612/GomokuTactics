package com.example.gomokutactics.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.gomokutactics.R;
import com.example.gomokutactics.models.MatchRecord;
import com.example.gomokutactics.models.Move;
import com.example.gomokutactics.utils.GamePrefs;
import com.example.gomokutactics.views.GomokuBoardView;

import java.util.ArrayList;
import java.util.List;

public class ReplayActivity extends AppCompatActivity {

    private GomokuBoardView replayBoardView;
    private TextView tvReplayHeader, tvReplayStepInfo;
    private Button btnReplayPlayPause;

    private final List<Move> allMoves = new ArrayList<>();
    private int currentStep = 0;
    private int boardSize = 15;
    private boolean isAutoPlaying = false;

    private final Handler autoPlayHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoPlayRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAutoPlaying || isFinishing() || isDestroyed()) return;
            if (currentStep < allMoves.size()) {
                stepForward();
                autoPlayHandler.postDelayed(this, 650);
            } else {
                isAutoPlaying = false;
                btnReplayPlayPause.setText(R.string.btn_replay_restart);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_replay);
        setContentView(R.layout.activity_replay);

        replayBoardView = findViewById(R.id.replayBoardView);
        tvReplayHeader = findViewById(R.id.tvReplayHeader);
        tvReplayStepInfo = findViewById(R.id.tvReplayStepInfo);
        Button btnReplayPrev = findViewById(R.id.btnReplayPrev);
        btnReplayPlayPause = findViewById(R.id.btnReplayPlayPause);
        Button btnReplayNext = findViewById(R.id.btnReplayNext);

        MatchRecord record = (MatchRecord) getIntent().getSerializableExtra("MATCH_RECORD");
        if (record != null) {
            boardSize = record.getBoardSize();
            String localizedMode = GamePrefs.getLocalizedGameMode(this, record.getGameMode());
            String localizedResult = GamePrefs.getLocalizedResult(this, record.getResult());
            tvReplayHeader.setText(getString(
                    R.string.replay_header_format, localizedMode, boardSize, localizedResult));
            parseMovesData(record.getMovesData());
        }

        replayBoardView.applyThemePaints();
        replayBoardView.setInteractive(false);
        replayBoardView.setBoardSize(boardSize);
        updateStepText();

        btnReplayNext.setOnClickListener(v -> {
            stopAutoPlay();
            stepForward();
        });

        btnReplayPrev.setOnClickListener(v -> {
            stopAutoPlay();
            stepBackward();
        });

        btnReplayPlayPause.setOnClickListener(v -> {
            if (isAutoPlaying) {
                stopAutoPlay();
            } else {
                if (currentStep >= allMoves.size()) {
                    currentStep = 0;
                    replayBoardView.setBoardSize(boardSize);
                }
                isAutoPlaying = true;
                btnReplayPlayPause.setText(R.string.btn_replay_pause);
                autoPlayHandler.post(autoPlayRunnable);
            }
        });
    }

    private void parseMovesData(String data) {
        allMoves.clear();
        if (data == null || data.trim().isEmpty()) return;
        String[] parts = data.split(";");
        for (String p : parts) {
            String[] coords = p.split(",");
            if (coords.length == 3) {
                try {
                    int r = Integer.parseInt(coords[0].trim());
                    int c = Integer.parseInt(coords[1].trim());
                    int player = Integer.parseInt(coords[2].trim());
                    allMoves.add(new Move(r, c, player));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private void stepForward() {
        if (currentStep < allMoves.size()) {
            Move m = allMoves.get(currentStep);
            replayBoardView.placePiece(m.row, m.col, m.player);
            currentStep++;
            updateStepText();
        }
    }

    private void stepBackward() {
        if (currentStep > 0) {
            replayBoardView.undoLastMoves(1);
            currentStep--;
            updateStepText();
        }
    }

    private void stopAutoPlay() {
        isAutoPlaying = false;
        btnReplayPlayPause.setText(R.string.btn_replay_auto);
        autoPlayHandler.removeCallbacks(autoPlayRunnable);
    }

    private void updateStepText() {
        tvReplayStepInfo.setText(getString(R.string.replay_step_info, currentStep, allMoves.size()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoPlay();
    }
}