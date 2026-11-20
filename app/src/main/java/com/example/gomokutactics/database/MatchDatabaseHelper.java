package com.example.gomokutactics.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.gomokutactics.models.MatchRecord;

import java.util.ArrayList;
import java.util.List;

public class MatchDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "GomokuMatches.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_MATCHES = "match_history";
    public static final String COL_ID = "id";
    public static final String COL_MODE = "game_mode";
    public static final String COL_SIZE = "board_size";
    public static final String COL_RESULT = "result";
    public static final String COL_MOVES_COUNT = "total_moves";
    public static final String COL_DATE = "played_date";
    public static final String COL_MOVES_DATA = "moves_data";

    public MatchDatabaseHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_MATCHES + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_MODE + " TEXT, " +
                COL_SIZE + " INTEGER, " +
                COL_RESULT + " TEXT, " +
                COL_MOVES_COUNT + " INTEGER, " +
                COL_DATE + " TEXT, " +
                COL_MOVES_DATA + " TEXT)";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MATCHES);
        onCreate(db);
    }

    public synchronized long insertMatch(String mode, int boardSize, String result, int totalMoves, String date, String movesData) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_MODE, mode);
        cv.put(COL_SIZE, boardSize);
        cv.put(COL_RESULT, result);
        cv.put(COL_MOVES_COUNT, totalMoves);
        cv.put(COL_DATE, date);
        cv.put(COL_MOVES_DATA, movesData);
        return db.insert(TABLE_MATCHES, null, cv);
    }

    public synchronized List<MatchRecord> getMatches(String resultFilter, String query) {
        List<MatchRecord> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE_MATCHES + " WHERE 1=1");
        List<String> args = new ArrayList<>();

        if (resultFilter != null && !"ALL".equals(resultFilter)) {
            sql.append(" AND ").append(COL_RESULT).append(" LIKE ?");
            args.add("%" + resultFilter + "%");
        }
        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (").append(COL_MODE).append(" LIKE ? OR CAST(").append(COL_SIZE).append(" AS TEXT) LIKE ?)");
            String pattern = "%" + query.trim() + "%";
            args.add(pattern);
            args.add(pattern);
        }
        sql.append(" ORDER BY ").append(COL_ID).append(" DESC");

        try (Cursor cursor = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
            if (cursor.moveToFirst()) {
                int idxId = cursor.getColumnIndexOrThrow(COL_ID);
                int idxMode = cursor.getColumnIndexOrThrow(COL_MODE);
                int idxSize = cursor.getColumnIndexOrThrow(COL_SIZE);
                int idxResult = cursor.getColumnIndexOrThrow(COL_RESULT);
                int idxMovesCount = cursor.getColumnIndexOrThrow(COL_MOVES_COUNT);
                int idxDate = cursor.getColumnIndexOrThrow(COL_DATE);
                int idxMovesData = cursor.getColumnIndexOrThrow(COL_MOVES_DATA);

                do {
                    list.add(new MatchRecord(
                            cursor.getInt(idxId),
                            cursor.getString(idxMode),
                            cursor.getInt(idxSize),
                            cursor.getString(idxResult),
                            cursor.getInt(idxMovesCount),
                            cursor.getString(idxDate),
                            cursor.getString(idxMovesData)
                    ));
                } while (cursor.moveToNext());
            }
        }
        return list;
    }

    public synchronized int getTotalMatches() {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MATCHES, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public synchronized int getWinMatches() {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MATCHES + " WHERE " + COL_RESULT + " = 'THẮNG'", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public synchronized boolean deleteMatch(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_MATCHES, COL_ID + "=?", new String[]{String.valueOf(id)});
        return rows > 0;
    }
}