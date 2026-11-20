package com.example.gomokutactics.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gomokutactics.R;
import com.example.gomokutactics.adapters.MatchHistoryAdapter;
import com.example.gomokutactics.database.MatchDatabaseHelper;
import com.example.gomokutactics.models.MatchRecord;
import com.example.gomokutactics.utils.GamePrefs;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView rvMatchHistory;
    private TextView tvEmptyHistory;
    private MatchHistoryAdapter adapter;
    private final List<MatchRecord> matchList = new ArrayList<>();
    private MatchDatabaseHelper dbHelper;

    private String currentResultFilter = "ALL";
    private String currentQuery = "";
    private final String[] filterDbKeys = {"ALL", "THẮNG", "THUA", "HÒA"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_history);
        setContentView(R.layout.activity_history);

        rvMatchHistory = findViewById(R.id.rvMatchHistory);
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory);
        Spinner spinnerFilterResult = findViewById(R.id.spinnerFilterResult);
        SearchView searchViewHistory = findViewById(R.id.searchViewHistory);

        dbHelper = new MatchDatabaseHelper(this);
        rvMatchHistory.setLayoutManager(new LinearLayoutManager(this));
        registerForContextMenu(rvMatchHistory);

        adapter = new MatchHistoryAdapter(this, matchList, this::openReplay);
        rvMatchHistory.setAdapter(adapter);

        String[] localizedFilterLabels = {
                getString(R.string.filter_all_results),
                getString(R.string.result_win),
                getString(R.string.result_loss),
                getString(R.string.result_draw)
        };
        ArrayAdapter<String> spAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, localizedFilterLabels);
        spinnerFilterResult.setAdapter(spAdapter);

        spinnerFilterResult.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentResultFilter = filterDbKeys[position];
                loadMatches();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        searchViewHistory.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentQuery = query;
                loadMatches();
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                currentQuery = newText;
                loadMatches();
                return true;
            }
        });

        loadMatches();
    }

    private void loadMatches() {
        List<MatchRecord> freshList = dbHelper.getMatches(currentResultFilter, currentQuery);
        adapter.updateItems(freshList);
        tvEmptyHistory.setVisibility(matchList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openReplay(MatchRecord record) {
        Intent intent = new Intent(this, ReplayActivity.class);
        intent.putExtra("MATCH_RECORD", record);
        startActivity(intent);
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        int pos = adapter.getPosition();
        if (pos < 0 || pos >= matchList.size()) return super.onContextItemSelected(item);

        MatchRecord selected = matchList.get(pos);
        int id = item.getItemId();

        if (id == R.id.ctx_replay_match) {
            openReplay(selected);
            return true;
        } else if (id == R.id.ctx_share_match) {
            String localizedMode = GamePrefs.getLocalizedGameMode(this, selected.getGameMode());
            String localizedResult = GamePrefs.getLocalizedResult(this, selected.getResult());

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, getString(
                    R.string.history_share_match_text,
                    localizedMode, selected.getBoardSize(), localizedResult, selected.getTotalMoves()));
            startActivity(Intent.createChooser(shareIntent, getString(R.string.chooser_share_match)));
            return true;
        } else if (id == R.id.ctx_delete_match) {
            dbHelper.deleteMatch(selected.getId());
            Toast.makeText(this, R.string.toast_match_deleted, Toast.LENGTH_SHORT).show();
            loadMatches();
            return true;
        }
        return super.onContextItemSelected(item);
    }
}