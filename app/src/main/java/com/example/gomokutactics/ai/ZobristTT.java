package com.example.gomokutactics.ai;

import java.util.Arrays;
import java.util.Random;

/**
 * Hệ thống lưu trữ trạng thái thế cờ tốc độ cao sử dụng:
 * - Zobrist Hashing 64-bit cập nhật O(1)
 * - Transposition Table dạng mảng nguyên thủy (Zero-GC Allocation)
 * - Killer Moves & History Heuristic giúp sắp xếp nhánh tối ưu
 */
public class ZobristTT {

    public static final int MAX_BOARD_SIZE = 32;
    public static final int MAX_PLY = 32;

    // Cờ trạng thái nút trong Transposition Table
    public static final byte FLAG_NONE = 0;
    public static final byte FLAG_EXACT = 1;
    public static final byte FLAG_LOWERBOUND = 2; // Alpha cutoff (Beta)
    public static final byte FLAG_UPPERBOUND = 3; // Fail-low (Alpha)

    // Kích thước bảng băm: 2^18 = 262,144 entries (~4.5MB RAM, cực nhẹ & nhanh)
    private static final int TT_BITS = 18;
    private static final int TT_SIZE = 1 << TT_BITS;
    private static final int TT_MASK = TT_SIZE - 1;

    private static final long[][][] ZOBRIST_TABLE = new long[MAX_BOARD_SIZE][MAX_BOARD_SIZE][3];
    private static final long ZOBRIST_SIDE_TO_MOVE;

    static {
        Random rng = new Random(20261116L); // Hạt giống cố định để tái lập chính xác
        for (int r = 0; r < MAX_BOARD_SIZE; r++) {
            for (int c = 0; c < MAX_BOARD_SIZE; c++) {
                ZOBRIST_TABLE[r][c][1] = rng.nextLong();
                ZOBRIST_TABLE[r][c][2] = rng.nextLong();
            }
        }
        ZOBRIST_SIDE_TO_MOVE = rng.nextLong();
    }

    // Dữ liệu Transposition Table dùng mảng nguyên thủy tránh tạo Object rác
    private final long[] ttHash = new long[TT_SIZE];
    private final int[] ttScore = new int[TT_SIZE];
    private final byte[] ttDepth = new byte[TT_SIZE];
    private final byte[] ttFlag = new byte[TT_SIZE];
    private final short[] ttBestMove = new short[TT_SIZE]; // Mã hóa (row << 8) | col

    // Killer Heuristic: Lưu 2 nước đi gây cắt tỉa tốt nhất ở mỗi độ sâu ply
    private final int[] killer1 = new int[MAX_PLY];
    private final int[] killer2 = new int[MAX_PLY];

    // History Heuristic: Thưởng điểm cho các ô thường xuyên gây cắt tỉa Alpha-Beta
    private final int[][][] history = new int[3][MAX_BOARD_SIZE][MAX_BOARD_SIZE];

    public ZobristTT() {
        clearHeuristics();
    }

    public void clearAll() {
        Arrays.fill(ttFlag, FLAG_NONE);
        clearHeuristics();
    }

    public void clearHeuristics() {
        Arrays.fill(killer1, -1);
        Arrays.fill(killer2, -1);
        for (int p = 1; p <= 2; p++) {
            for (int r = 0; r < MAX_BOARD_SIZE; r++) {
                Arrays.fill(history[p][r], 0);
            }
        }
    }

    public static long computeBoardHash(int[][] board, int size, int sideToMove) {
        long h = 0L;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int p = board[r][c];
                if (p == 1 || p == 2) {
                    h ^= ZOBRIST_TABLE[r][c][p];
                }
            }
        }
        if (sideToMove == 2) {
            h ^= ZOBRIST_SIDE_TO_MOVE;
        }
        return h;
    }

    public static long updateHash(long currentHash, int r, int c, int player) {
        return currentHash ^ ZOBRIST_TABLE[r][c][player] ^ ZOBRIST_SIDE_TO_MOVE;
    }

    public static short packMove(int r, int c) {
        return (short) ((r << 8) | (c & 0xFF));
    }

    public static int unpackRow(int packed) {
        return (packed >> 8) & 0xFF;
    }

    public static int unpackCol(int packed) {
        return packed & 0xFF;
    }

    public int probeIndex(long hash) {
        int idx = (int) (hash & TT_MASK);
        if (ttFlag[idx] != FLAG_NONE && ttHash[idx] == hash) {
            return idx;
        }
        return -1;
    }

    public byte getFlag(int idx) {
        return ttFlag[idx];
    }

    public int getDepth(int idx) {
        return ttDepth[idx];
    }

    public int getScore(int idx) {
        return ttScore[idx];
    }

    public short getBestMove(int idx) {
        return ttBestMove[idx];
    }

    public void store(long hash, int depth, int score, byte flag, int bestRow, int bestCol) {
        int idx = (int) (hash & TT_MASK);
        if (ttFlag[idx] == FLAG_NONE || ttHash[idx] != hash || depth >= ttDepth[idx]) {
            ttHash[idx] = hash;
            ttScore[idx] = score;
            ttDepth[idx] = (byte) depth;
            ttFlag[idx] = flag;
            if (bestRow >= 0 && bestCol >= 0) {
                ttBestMove[idx] = packMove(bestRow, bestCol);
            } else {
                ttBestMove[idx] = -1;
            }
        }
    }

    public void recordKiller(int ply, int r, int c) {
        if (ply < 0 || ply >= MAX_PLY) return;
        int packed = packMove(r, c);
        if (killer1[ply] != packed) {
            killer2[ply] = killer1[ply];
            killer1[ply] = packed;
        }
    }

    public int getKillerBonus(int ply, int r, int c) {
        if (ply < 0 || ply >= MAX_PLY) return 0;
        int packed = packMove(r, c);
        if (killer1[ply] == packed) return 25_000;
        if (killer2[ply] == packed) return 15_000;
        return 0;
    }

    public void recordHistory(int player, int r, int c, int depth) {
        if ((player == 1 || player == 2) && r >= 0 && r < MAX_BOARD_SIZE && c >= 0 && c < MAX_BOARD_SIZE) {
            history[player][r][c] += depth * depth * 16;
            if (history[player][r][c] > 100_000) {
                history[player][r][c] = 100_000;
            }
        }
    }

    public int getHistoryScore(int player, int r, int c) {
        return history[player][r][c];
    }
}