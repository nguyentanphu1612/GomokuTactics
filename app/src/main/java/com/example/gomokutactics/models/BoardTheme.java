package com.example.gomokutactics.models;

public class BoardTheme {
    private int id;
    private String name;
    private String description;
    private int boardBgColor;
    private int gridLineColor;
    private int p1Color;
    private int p2Color;
    private boolean isCirclePiece; // false: X/O truyền thống, true: Quân cờ tròn Đen/Trắng - Lửa/Băng

    public BoardTheme(int id, String name, String description, int boardBgColor, int gridLineColor, int p1Color, int p2Color, boolean isCirclePiece) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.boardBgColor = boardBgColor;
        this.gridLineColor = gridLineColor;
        this.p1Color = p1Color;
        this.p2Color = p2Color;
        this.isCirclePiece = isCirclePiece;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getBoardBgColor() { return boardBgColor; }
    public int getGridLineColor() { return gridLineColor; }
    public int getP1Color() { return p1Color; }
    public int getP2Color() { return p2Color; }
    public boolean isCirclePiece() { return isCirclePiece; }
}