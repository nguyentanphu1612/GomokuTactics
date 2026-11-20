package com.example.gomokutactics.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.gomokutactics.R;
import com.example.gomokutactics.database.MatchDatabaseHelper;
import com.example.gomokutactics.utils.GamePrefs;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatTotal, tvStatWins, tvStatWinRate;
    private RadioGroup rgGameMode;
    private Spinner spinnerBoardSize, spinnerTimeLimit, spinnerDifficulty;
    private LinearLayout layoutDifficultySection;
    private BottomNavigationView bottomNav;
    private MatchDatabaseHelper dbHelper;

    private boolean isSyncingControls = false;

    private final int[] sizeValues = {15, 20, 30};
    private final int[] timeValues = {0, 5, 10, 15};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_main);
        setContentView(R.layout.activity_main);

        dbHelper = new MatchDatabaseHelper(this);

        tvStatTotal = findViewById(R.id.tvStatTotal);
        tvStatWins = findViewById(R.id.tvStatWins);
        tvStatWinRate = findViewById(R.id.tvStatWinRate);
        rgGameMode = findViewById(R.id.rgGameMode);
        spinnerBoardSize = findViewById(R.id.spinnerBoardSize);
        spinnerTimeLimit = findViewById(R.id.spinnerTimeLimit);
        spinnerDifficulty = findViewById(R.id.spinnerDifficulty);
        layoutDifficultySection = findViewById(R.id.layoutDifficultySection);
        bottomNav = findViewById(R.id.bottomNavigation);

        setupSpinners();
        setupEvents();
    }

    private void setupSpinners() {
        String[] sizeLabels = {
                getString(R.string.size_15),
                getString(R.string.size_20),
                getString(R.string.size_30)
        };
        spinnerBoardSize.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sizeLabels));

        String[] timeLabels = {
                getString(R.string.time_unlimited),
                getString(R.string.time_5m),
                getString(R.string.time_10m),
                getString(R.string.time_15m)
        };
        spinnerTimeLimit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, timeLabels));

        String[] diffLabels = {
                getString(R.string.diff_easy),
                getString(R.string.diff_hard),
                getString(R.string.diff_extreme)
        };
        spinnerDifficulty.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, diffLabels));

        syncControlsFromPrefs();

        spinnerBoardSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isSyncingControls) {
                    GamePrefs.setBoardSize(MainActivity.this, sizeValues[position]);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerTimeLimit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isSyncingControls) {
                    GamePrefs.setTimeLimitMinutes(MainActivity.this, timeValues[position]);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerDifficulty.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isSyncingControls) {
                    GamePrefs.setDifficulty(MainActivity.this, position + 1);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupEvents() {
        rgGameMode.setOnCheckedChangeListener((group, checkedId) -> {
            boolean vsAI = (checkedId == R.id.rbVsAI);
            if (!isSyncingControls) {
                GamePrefs.setVsAI(this, vsAI);
            }
            layoutDifficultySection.setVisibility(vsAI ? View.VISIBLE : View.GONE);
        });

        findViewById(R.id.btnStartGame).setOnClickListener(v -> launchOfflineGame());

        findViewById(R.id.btnPlayOnline).setOnClickListener(v ->
                startActivity(new Intent(this, OnlineLobbyActivity.class))
        );

        findViewById(R.id.cardQuickReplay).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class))
        );

        findViewById(R.id.cardQuickSkins).setOnClickListener(v ->
                startActivity(new Intent(this, SkinShopActivity.class))
        );

        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_play) {
                launchOfflineGame();
                return true;
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, HistoryActivity.class));
                return true;
            } else if (id == R.id.nav_skins) {
                startActivity(new Intent(this, SkinShopActivity.class));
                return true;
            }
            return id == R.id.nav_home;
        });
    }

    private void launchOfflineGame() {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra("GAME_MODE", GamePrefs.isVsAI(this) ? "AI" : "LOCAL");
        intent.putExtra("BOARD_SIZE", GamePrefs.getBoardSize(this));
        intent.putExtra("TIME_LIMIT_MINUTES", GamePrefs.getTimeLimitMinutes(this));
        intent.putExtra("DIFFICULTY", GamePrefs.getDifficulty(this));
        intent.putExtra("BLOCK_TWO_ENDS", GamePrefs.isBlockTwoEnds(this));
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out);
    }

    private void syncControlsFromPrefs() {
        isSyncingControls = true;
        try {
            int savedSize = GamePrefs.getBoardSize(this);
            for (int i = 0; i < sizeValues.length; i++) {
                if (sizeValues[i] == savedSize) {
                    spinnerBoardSize.setSelection(i);
                    break;
                }
            }

            int savedTime = GamePrefs.getTimeLimitMinutes(this);
            for (int i = 0; i < timeValues.length; i++) {
                if (timeValues[i] == savedTime) {
                    spinnerTimeLimit.setSelection(i);
                    break;
                }
            }

            int diff = GamePrefs.getDifficulty(this);
            spinnerDifficulty.setSelection(Math.max(0, Math.min(2, diff - 1)));

            boolean vsAI = GamePrefs.isVsAI(this);
            rgGameMode.check(vsAI ? R.id.rbVsAI : R.id.rbVsPlayer);
            layoutDifficultySection.setVisibility(vsAI ? View.VISIBLE : View.GONE);
        } finally {
            isSyncingControls = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNav != null && bottomNav.getSelectedItemId() != R.id.nav_home) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
        syncControlsFromPrefs();
        updateStats();
    }

    private void updateStats() {
        int total = dbHelper.getTotalMatches();
        int wins = dbHelper.getWinMatches();
        double rate = (total == 0) ? 0.0 : (wins * 100.0 / total);

        tvStatTotal.setText(String.valueOf(total));
        tvStatWins.setText(String.valueOf(wins));
        tvStatWinRate.setText(String.format(Locale.US, "%.0f%%", rate));
    }

    private void changeAppLanguage(String languageTag) {
        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(languageTag);
        AppCompatDelegate.setApplicationLocales(appLocale);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.lang_vi) {
            changeAppLanguage("vi");
            return true;
        } else if (id == R.id.lang_en) {
            changeAppLanguage("en");
            return true;
        } else if (id == R.id.size_15) {
            GamePrefs.setBoardSize(this, 15);
            syncControlsFromPrefs();
            return true;
        } else if (id == R.id.size_20) {
            GamePrefs.setBoardSize(this, 20);
            syncControlsFromPrefs();
            return true;
        } else if (id == R.id.size_30) {
            GamePrefs.setBoardSize(this, 30);
            syncControlsFromPrefs();
            return true;
        } else if (id == R.id.diff_easy) {
            GamePrefs.setDifficulty(this, 1);
            syncControlsFromPrefs();
            return true;
        } else if (id == R.id.diff_hard) {
            GamePrefs.setDifficulty(this, 2);
            syncControlsFromPrefs();
            return true;
        } else if (id == R.id.diff_extreme) {
            GamePrefs.setDifficulty(this, 3);
            syncControlsFromPrefs();
            return true;
        } else if (id == R.id.menu_rules) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}