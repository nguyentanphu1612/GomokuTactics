package com.example.gomokutactics.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import com.example.gomokutactics.R;
import com.example.gomokutactics.models.BoardTheme;

import java.util.ArrayList;
import java.util.List;

public class GamePrefs {
    private static final String PREF_NAME = "GomokuTacticsPrefs";
    private static final String KEY_BOARD_SIZE = "board_size";
    private static final String KEY_TIME_LIMIT = "time_limit_minutes"; // 0: Không giới hạn, 5, 10, 15
    private static final String KEY_DIFFICULTY = "ai_difficulty";
    private static final String KEY_VS_AI = "vs_ai";
    private static final String KEY_SKIN_ID = "skin_id";
    private static final String KEY_BLOCK_TWO_ENDS = "block_two_ends";
    private static final String KEY_SOUND = "sound_enabled";

    public static void setBoardSize(Context ctx, int size) {
        getPrefs(ctx).edit().putInt(KEY_BOARD_SIZE, size).apply();
    }

    public static int getBoardSize(Context ctx) {
        return getPrefs(ctx).getInt(KEY_BOARD_SIZE, 15);
    }

    public static void setTimeLimitMinutes(Context ctx, int minutes) {
        getPrefs(ctx).edit().putInt(KEY_TIME_LIMIT, minutes).apply();
    }

    public static int getTimeLimitMinutes(Context ctx) {
        return getPrefs(ctx).getInt(KEY_TIME_LIMIT, 5);
    }

    public static void setDifficulty(Context ctx, int diff) {
        getPrefs(ctx).edit().putInt(KEY_DIFFICULTY, diff).apply();
    }

    public static int getDifficulty(Context ctx) {
        return getPrefs(ctx).getInt(KEY_DIFFICULTY, 2);
    }

    public static void setVsAI(Context ctx, boolean vsAI) {
        getPrefs(ctx).edit().putBoolean(KEY_VS_AI, vsAI).apply();
    }

    public static boolean isVsAI(Context ctx) {
        return getPrefs(ctx).getBoolean(KEY_VS_AI, true);
    }

    public static void setSkinId(Context ctx, int skinId) {
        getPrefs(ctx).edit().putInt(KEY_SKIN_ID, skinId).apply();
    }

    public static int getSkinId(Context ctx) {
        return getPrefs(ctx).getInt(KEY_SKIN_ID, 0);
    }

    public static void setBlockTwoEnds(Context ctx, boolean block) {
        getPrefs(ctx).edit().putBoolean(KEY_BLOCK_TWO_ENDS, block).apply();
    }

    public static boolean isBlockTwoEnds(Context ctx) {
        return getPrefs(ctx).getBoolean(KEY_BLOCK_TWO_ENDS, false);
    }

    public static void setSoundEnabled(Context ctx, boolean enabled) {
        getPrefs(ctx).edit().putBoolean(KEY_SOUND, enabled).apply();
    }

    public static boolean isSoundEnabled(Context ctx) {
        return getPrefs(ctx).getBoolean(KEY_SOUND, true);
    }

    private static SharedPreferences getPrefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static List<BoardTheme> getAllThemes(Context ctx) {
        List<BoardTheme> list = new ArrayList<>();
        // 0: Gỗ Tuyết Tùng Cổ Điển (X / O)
        list.add(new BoardTheme(0,
                ctx.getString(R.string.theme_classic_name),
                ctx.getString(R.string.theme_classic_desc),
                Color.parseColor("#EBD0A4"), Color.parseColor("#7A5230"),
                Color.parseColor("#C81E1E"), Color.parseColor("#1E40AF"), false));
        // 1: Đêm Công Nghệ (X / O)
        list.add(new BoardTheme(1,
                ctx.getString(R.string.theme_cyber_name),
                ctx.getString(R.string.theme_cyber_desc),
                Color.parseColor("#0B1120"), Color.parseColor("#1E293B"),
                Color.parseColor("#FBBF24"), Color.parseColor("#34D399"), false));
        // 2: Bàn Cờ Vây Nhật Bản (Quân tròn 3D Đen / Trắng)
        list.add(new BoardTheme(2,
                ctx.getString(R.string.theme_go_name),
                ctx.getString(R.string.theme_go_desc),
                Color.parseColor("#DFBA84"), Color.parseColor("#3B2618"),
                Color.parseColor("#111827"), Color.parseColor("#F8FAFC"), true));
        // 3: Ngọc Bích Thanh Tịnh (X / O)
        list.add(new BoardTheme(3,
                ctx.getString(R.string.theme_jade_name),
                ctx.getString(R.string.theme_jade_desc),
                Color.parseColor("#D3F3E4"), Color.parseColor("#1E6F54"),
                Color.parseColor("#6D28D9"), Color.parseColor("#EA580C"), false));
        // 4: Anh Đào Kyoto (X / O)
        list.add(new BoardTheme(4,
                ctx.getString(R.string.theme_sakura_name),
                ctx.getString(R.string.theme_sakura_desc),
                Color.parseColor("#FCEEF5"), Color.parseColor("#B86B8B"),
                Color.parseColor("#BE123C"), Color.parseColor("#1E3A8A"), false));
        // 5: Lửa & Băng (Quân tròn 3D Cam Lửa / Xanh Băng)
        list.add(new BoardTheme(5,
                ctx.getString(R.string.theme_fire_ice_name),
                ctx.getString(R.string.theme_fire_ice_desc),
                Color.parseColor("#171C3A"), Color.parseColor("#2E386B"),
                Color.parseColor("#F97316"), Color.parseColor("#38BDF8"), true));
        return list;
    }

    public static BoardTheme getCurrentTheme(Context ctx) {
        int id = getSkinId(ctx);
        List<BoardTheme> list = getAllThemes(ctx);
        if (id >= 0 && id < list.size()) return list.get(id);
        return list.get(0);
    }

    /**
     * Chuyển đổi mã kết quả lưu trong SQLite sang ngôn ngữ hiện tại của ứng dụng.
     */
    public static String getLocalizedResult(Context ctx, String rawResult) {
        if (rawResult == null) return "";
        switch (rawResult) {
            case "THẮNG":
            case "WIN":
                return ctx.getString(R.string.result_win);
            case "THUA":
            case "LOSS":
                return ctx.getString(R.string.result_loss);
            case "HÒA":
            case "DRAW":
                return ctx.getString(R.string.result_draw);
            case "P1 THẮNG":
            case "P1 WIN":
                return ctx.getString(R.string.result_p1_win);
            case "P2 THẮNG":
            case "P2 WIN":
                return ctx.getString(R.string.result_p2_win);
            default:
                return rawResult;
        }
    }

    /**
     * Chuyển đổi tên chế độ chơi lưu trong SQLite sang ngôn ngữ hiện tại của ứng dụng.
     */
    public static String getLocalizedGameMode(Context ctx, String rawMode) {
        if (rawMode == null) return "";
        if ("2 Người chơi".equals(rawMode) || "2 Players".equals(rawMode)) {
            return ctx.getString(R.string.mode_2_players);
        }
        if ("AI (Dễ)".equals(rawMode) || "AI (Easy)".equals(rawMode)) {
            return ctx.getString(R.string.game_ai_mode_badge, ctx.getString(R.string.diff_easy));
        }
        if ("AI (Khó)".equals(rawMode) || "AI (Hard)".equals(rawMode)) {
            return ctx.getString(R.string.game_ai_mode_badge, ctx.getString(R.string.diff_hard));
        }
        if ("AI (Cực hạn)".equals(rawMode) || "AI (Extreme)".equals(rawMode)) {
            return ctx.getString(R.string.game_ai_mode_badge, ctx.getString(R.string.diff_extreme));
        }
        return rawMode;
    }
}