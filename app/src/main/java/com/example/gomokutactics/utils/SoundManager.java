package com.example.gomokutactics.utils;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Quản lý âm thanh & rung phản hồi theo cơ chế Singleton:
 * Tái sử dụng duy nhất 1 ToneGenerator để tránh rò rỉ native AudioTrack handle.
 */
public class SoundManager {

    private static final Object LOCK = new Object();
    private static ToneGenerator sharedToneGenerator;

    public static void playPlaceSound(Context ctx) {
        if (ctx == null || !GamePrefs.isSoundEnabled(ctx)) return;
        playTone(ToneGenerator.TONE_PROP_BEEP, 70);
        vibrate(ctx.getApplicationContext(), 25);
    }

    public static void playHintSound(Context ctx) {
        if (ctx == null || !GamePrefs.isSoundEnabled(ctx)) return;
        playTone(ToneGenerator.TONE_PROP_ACK, 150);
        vibrate(ctx.getApplicationContext(), 50);
    }

    public static void playWinSound(Context ctx) {
        if (ctx == null || !GamePrefs.isSoundEnabled(ctx)) return;
        playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350);
        vibrate(ctx.getApplicationContext(), 180);
    }

    private static void playTone(int toneType, int durationMs) {
        synchronized (LOCK) {
            try {
                if (sharedToneGenerator == null) {
                    sharedToneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
                }
                sharedToneGenerator.startTone(toneType, durationMs);
            } catch (Exception e) {
                // Nếu hệ thống thu hồi audio session, giải phóng và tạo lại ở lần sau
                releaseInternal();
            }
        }
    }

    public static void release() {
        synchronized (LOCK) {
            releaseInternal();
        }
    }

    private static void releaseInternal() {
        if (sharedToneGenerator != null) {
            try {
                sharedToneGenerator.release();
            } catch (Exception ignored) {
            }
            sharedToneGenerator = null;
        }
    }

    private static void vibrate(Context appCtx, long ms) {
        try {
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) appCtx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = (vm != null) ? vm.getDefaultVibrator() : null;
            } else {
                vibrator = (Vibrator) appCtx.getSystemService(Context.VIBRATOR_SERVICE);
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(ms);
                }
            }
        } catch (Exception ignored) {
        }
    }
}