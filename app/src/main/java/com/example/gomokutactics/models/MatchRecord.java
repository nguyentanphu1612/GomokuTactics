package com.example.gomokutactics.models;

import java.io.Serializable;

public class MatchRecord implements Serializable {
    private final int id;
    private final String gameMode;     // VD: "AI (Cực hạn)" hoặc "2 Người chơi"
    private final int boardSize;       // 15, 20, 30
    private final String result;       // "THẮNG", "THUA", "HÒA", "P1 THẮNG", "P2 THẮNG"
    private final int totalMoves;
    private final String playedDate;
    private final String movesData;    // Chuỗi tọa độ "7,7,1;7,8,2;..." để phát lại (Replay)

    public MatchRecord(int id, String gameMode, int boardSize, String result, int totalMoves, String playedDate, String movesData) {
        this.id = id;
        this.gameMode = gameMode;
        this.boardSize = boardSize;
        this.result = result;
        this.totalMoves = totalMoves;
        this.playedDate = playedDate;
        this.movesData = movesData;
    }

    public int getId() { return id; }
    public String getGameMode() { return gameMode; }
    public int getBoardSize() { return boardSize; }
    public String getResult() { return result; }
    public int getTotalMoves() { return totalMoves; }
    public String getPlayedDate() { return playedDate; }
    public String getMovesData() { return movesData; }
}