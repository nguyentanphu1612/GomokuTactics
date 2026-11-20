package com.example.gomokutactics.models;

import java.io.Serializable;

public class Move implements Serializable {
    public int row;
    public int col;
    public int player; // 1: Player X, 2: Player O / AI

    public Move(int row, int col, int player) {
        this.row = row;
        this.col = col;
        this.player = player;
    }
}