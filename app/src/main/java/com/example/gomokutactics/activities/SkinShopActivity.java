package com.example.gomokutactics.activities;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gomokutactics.R;
import com.example.gomokutactics.adapters.SkinAdapter;
import com.example.gomokutactics.utils.GamePrefs;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SkinShopActivity extends AppCompatActivity {

    private SkinAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_skins);
        setContentView(R.layout.activity_skin_shop);

        SwitchMaterial switchBlockTwoEnds = findViewById(R.id.switchBlockTwoEnds);
        SwitchMaterial switchSoundEffect = findViewById(R.id.switchSoundEffect);
        RecyclerView rvSkins = findViewById(R.id.rvSkins);

        switchBlockTwoEnds.setChecked(GamePrefs.isBlockTwoEnds(this));
        switchSoundEffect.setChecked(GamePrefs.isSoundEnabled(this));

        switchBlockTwoEnds.setOnCheckedChangeListener((buttonView, isChecked) -> {
            GamePrefs.setBlockTwoEnds(this, isChecked);
            Toast.makeText(this,
                    isChecked ? R.string.toast_block_two_on : R.string.toast_block_two_off,
                    Toast.LENGTH_SHORT).show();
        });

        switchSoundEffect.setOnCheckedChangeListener((buttonView, isChecked) ->
                GamePrefs.setSoundEnabled(this, isChecked)
        );

        rvSkins.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SkinAdapter(this, GamePrefs.getAllThemes(this), GamePrefs.getSkinId(this), theme -> {
            GamePrefs.setSkinId(this, theme.getId());
            adapter.setSelectedId(theme.getId());
            Toast.makeText(this,
                    getString(R.string.toast_skin_applied, theme.getName()),
                    Toast.LENGTH_SHORT).show();
        });
        rvSkins.setAdapter(adapter);
    }
}