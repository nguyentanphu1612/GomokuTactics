package com.example.gomokutactics.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.gomokutactics.R;

public class SplashActivity extends AppCompatActivity {

    private final Handler splashHandler = new Handler(Looper.getMainLooper());
    private final Runnable navigateRunnable = () -> {
        if (!isFinishing() && !isDestroyed()) {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TextView logo = findViewById(R.id.imgSplashLogo);
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        logo.startAnimation(fadeIn);

        splashHandler.postDelayed(navigateRunnable, 1600);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        splashHandler.removeCallbacks(navigateRunnable);
    }
}