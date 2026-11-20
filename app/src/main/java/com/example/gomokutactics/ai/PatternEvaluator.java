package com.example.gomokutactics.ai;

/**
 * Bộ phân tích mẫu hình thế cờ Ca-Rô (Gomoku Pattern Recognition Engine).
 * Hỗ trợ đầy đủ các hình thế liền mạch, hình thế nhảy cách (gapped patterns),
 * đòn phối hợp đa hướng (Forks) và luật chặn 2 đầu (blockTwoEnds).
 */
public class PatternEvaluator {

    // Mã định danh cấp độ hình thế trên 1 trục
    public static final int PAT_NONE = 0;
    public static final int PAT_SLEEP_TWO = 1;    // OXX... hoặc OX.X..
    public static final int PAT_OPEN_TWO = 2;     // ..XX.. hoặc .X.X. hoặc .X..X.
    public static final int PAT_SLEEP_THREE = 3;  // OXXX.. hoặc OXX.X. hoặc OX.XX.
    public static final int PAT_OPEN_THREE = 4;   // .XXX. hoặc .XX.X. hoặc .X.XX.
    public static final int PAT_DEAD_FOUR = 5;    // OXXXX. hoặc XXX.X hoặc XX.XX hoặc X.XXX
    public static final int PAT_OPEN_FOUR = 6;    // .XXXX. (Bất khả kháng)
    public static final int PAT_FIVE = 7;         // XXXXX (Chiến thắng)

    // Trọng số điểm chiến thuật
    public static final int SCORE_FIVE = 10_000_000;
    public static final int SCORE_OPEN_FOUR = 1_500_000;
    public static final int SCORE_DOUBLE_FOUR = 900_000;
    public static final int SCORE_FOUR_THREE = 750_000;
    public static final int SCORE_DOUBLE_THREE = 280_000;
    public static final int SCORE_DEAD_FOUR = 45_000;
    public static final int SCORE_OPEN_THREE = 32_000;
    public static final int SCORE_SLEEP_THREE = 4_200;
    public static final int SCORE_OPEN_TWO = 1_800;
    public static final int SCORE_SLEEP_TWO = 280;

    public static final int[][] DIRS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    /**
     * Kết quả phân tích tổng hợp của một ô cờ trên cả 4 hướng
     */
    public static class PointThreat {
        public int fiveCount = 0;
        public int openFourCount = 0;
        public int deadFourCount = 0;
        public int openThreeCount = 0;
        public int sleepThreeCount = 0;
        public int openTwoCount = 0;
        public int sleepTwoCount = 0;
        public int totalScore = 0;

        public void reset() {
            fiveCount = 0;
            openFourCount = 0;
            deadFourCount = 0;
            openThreeCount = 0;
            sleepThreeCount = 0;
            openTwoCount = 0;
            sleepTwoCount = 0;
            totalScore = 0;
        }

        public boolean isWin() {
            return fiveCount > 0;
        }

        public boolean isLethalOpenFour() {
            return openFourCount > 0 || deadFourCount >= 2;
        }

        public boolean isFourThreeCombo() {
            return deadFourCount >= 1 && openThreeCount >= 1;
        }

        public boolean isDoubleThreeCombo() {
            return openThreeCount >= 2;
        }

        public boolean hasFourThreat() {
            return fiveCount > 0 || openFourCount > 0 || deadFourCount > 0;
        }

        public boolean hasAnyThreat() {
            return hasFourThreat() || openThreeCount > 0;
        }
    }

    /**
     * Kiểm tra nhanh xem đặt quân `player` vào (r, c) có tạo thành 5 quân thắng ngay lập tức không.
     */
    public static boolean isWinningMove(int[][] board, int size, int r, int c, int player, boolean blockTwoEnds) {
        int opponent = (player == 1) ? 2 : 1;
        for (int[] dir : DIRS) {
            int count = 1;
            int blockedEnds = 0;

            int nr = r + dir[0], nc = c + dir[1];
            while (nr >= 0 && nr < size && nc >= 0 && nc < size && board[nr][nc] == player) {
                count++;
                nr += dir[0];
                nc += dir[1];
            }
            if (nr < 0 || nr >= size || nc < 0 || nc >= size || board[nr][nc] == opponent) {
                blockedEnds++;
            }

            nr = r - dir[0];
            nc = c - dir[1];
            while (nr >= 0 && nr < size && nc >= 0 && nc < size && board[nr][nc] == player) {
                count++;
                nr -= dir[0];
                nc -= dir[1];
            }
            if (nr < 0 || nr >= size || nc < 0 || nc >= size || board[nr][nc] == opponent) {
                blockedEnds++;
            }

            if (count >= 5 && (!blockTwoEnds || blockedEnds < 2)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Phân tích toàn diện giá trị chiến thuật nếu `player` đặt quân tại (r, c).
     */
    public static void evaluatePoint(int[][] board, int size, int r, int c, int player,
                                     boolean blockTwoEnds, int[] lineBuffer, PointThreat out) {
        out.reset();
        int opponent = (player == 1) ? 2 : 1;

        for (int d = 0; d < 4; d++) {
            int dr = DIRS[d][0];
            int dc = DIRS[d][1];

            extractWindow(board, size, r, c, dr, dc, player, opponent, lineBuffer);
            int pat = classifyWindow(lineBuffer, player, opponent, blockTwoEnds);
            accumulatePattern(out, pat);
        }

        out.totalScore = computeThreatScore(out);
    }

    private static void extractWindow(int[][] board, int size, int r, int c, int dr, int dc,
                                      int player, int opponent, int[] lineBuffer) {
        for (int k = -4; k <= 4; k++) {
            if (k == 0) {
                lineBuffer[4] = player;
                continue;
            }
            int nr = r + k * dr;
            int nc = c + k * dc;
            if (nr < 0 || nr >= size || nc < 0 || nc >= size) {
                lineBuffer[k + 4] = opponent;
            } else {
                lineBuffer[k + 4] = board[nr][nc];
            }
        }
    }

    private static void accumulatePattern(PointThreat out, int pat) {
        switch (pat) {
            case PAT_FIVE:
                out.fiveCount++;
                break;
            case PAT_OPEN_FOUR:
                out.openFourCount++;
                break;
            case PAT_DEAD_FOUR:
                out.deadFourCount++;
                break;
            case PAT_OPEN_THREE:
                out.openThreeCount++;
                break;
            case PAT_SLEEP_THREE:
                out.sleepThreeCount++;
                break;
            case PAT_OPEN_TWO:
                out.openTwoCount++;
                break;
            case PAT_SLEEP_TWO:
                out.sleepTwoCount++;
                break;
        }
    }

    private static int computeThreatScore(PointThreat out) {
        if (out.fiveCount > 0) {
            return SCORE_FIVE;
        }

        int score = 0;
        if (out.openFourCount > 0) {
            score += SCORE_OPEN_FOUR * out.openFourCount;
        }
        if (out.deadFourCount >= 2) {
            score += SCORE_DOUBLE_FOUR;
        } else if (out.deadFourCount == 1 && out.openThreeCount >= 1) {
            score += SCORE_FOUR_THREE;
        } else if (out.openThreeCount >= 2) {
            score += SCORE_DOUBLE_THREE;
        }

        score += out.deadFourCount * SCORE_DEAD_FOUR;
        score += out.openThreeCount * SCORE_OPEN_THREE;
        score += out.sleepThreeCount * SCORE_SLEEP_THREE;
        score += out.openTwoCount * SCORE_OPEN_TWO;
        score += out.sleepTwoCount * SCORE_SLEEP_TWO;

        if (out.openThreeCount >= 1 && out.sleepThreeCount >= 1) {
            score += 12_000;
        }
        if (out.openTwoCount >= 2) {
            score += 4_500 * (out.openTwoCount - 1);
        }

        return score;
    }

    /**
     * Phân loại hình thế trên mảng 9 ô (chỉ số 0..8, tâm tại 4).
     */
    public static int classifyWindow(int[] line, int player, int opponent, boolean blockTwoEnds) {
        int leftIdx = 4;
        while (leftIdx > 0 && line[leftIdx - 1] == player) {
            leftIdx--;
        }
        int rightIdx = 4;
        while (rightIdx < 8 && line[rightIdx + 1] == player) {
            rightIdx++;
        }

        int count = rightIdx - leftIdx + 1;

        // 1. Kiểm tra 5 quân trở lên
        if (count >= 5) {
            if (!blockTwoEnds) return PAT_FIVE;
            int blockedEnds = 0;
            if (leftIdx > 0 && line[leftIdx - 1] == opponent) blockedEnds++;
            if (rightIdx < 8 && line[rightIdx + 1] == opponent) blockedEnds++;
            return (blockedEnds < 2) ? PAT_FIVE : PAT_NONE;
        }

        boolean leftEmpty = (leftIdx > 0 && line[leftIdx - 1] == 0);
        boolean rightEmpty = (rightIdx < 8 && line[rightIdx + 1] == 0);

        if (!leftEmpty && !rightEmpty) {
            return PAT_NONE;
        }

        // 2. Cụm 4 quân liên tiếp (XXXX)
        if (count == 4) {
            if (leftEmpty && rightEmpty) {
                return PAT_OPEN_FOUR; // .XXXX.
            }
            if (leftEmpty) {
                if (blockTwoEnds && leftIdx - 2 >= 0 && line[leftIdx - 2] == opponent) {
                    return PAT_NONE; // O.XXXXO
                }
                return PAT_DEAD_FOUR;
            }
            if (rightEmpty) {
                if (blockTwoEnds && rightIdx + 2 <= 8 && line[rightIdx + 2] == opponent) {
                    return PAT_NONE; // OXXXX.O
                }
                return PAT_DEAD_FOUR;
            }
        }

        // 3. Cụm 3 quân liên tiếp (XXX)
        if (count == 3) {
            // Tứ nhảy cách: X.XXX hoặc XXX.X
            if (leftEmpty && leftIdx - 2 >= 0 && line[leftIdx - 2] == player) {
                if (!blockTwoEnds || !isBothEndsBlocked(line, leftIdx - 3, rightIdx + 1, opponent)) {
                    return PAT_DEAD_FOUR;
                }
            }
            if (rightEmpty && rightIdx + 2 <= 8 && line[rightIdx + 2] == player) {
                if (!blockTwoEnds || !isBothEndsBlocked(line, leftIdx - 1, rightIdx + 3, opponent)) {
                    return PAT_DEAD_FOUR;
                }
            }

            // Tam mở (.XXX.)
            if (leftEmpty && rightEmpty) {
                boolean leftOuterEmpty = (leftIdx - 2 >= 0 && line[leftIdx - 2] == 0);
                boolean rightOuterEmpty = (rightIdx + 2 <= 8 && line[rightIdx + 2] == 0);
                if (leftOuterEmpty || rightOuterEmpty) {
                    return PAT_OPEN_THREE;
                }
                return blockTwoEnds ? PAT_NONE : PAT_SLEEP_THREE;
            }

            // Tam ngủ (OXXX.. hoặc ..XXXO)
            if (leftEmpty && leftIdx - 2 >= 0 && line[leftIdx - 2] == 0) {
                if (blockTwoEnds && leftIdx - 3 >= 0 && line[leftIdx - 3] == opponent) {
                    return PAT_NONE;
                }
                return PAT_SLEEP_THREE;
            }
            if (rightEmpty && rightIdx + 2 <= 8 && line[rightIdx + 2] == 0) {
                if (blockTwoEnds && rightIdx + 3 <= 8 && line[rightIdx + 3] == opponent) {
                    return PAT_NONE;
                }
                return PAT_SLEEP_THREE;
            }
            return PAT_NONE;
        }

        // 4. Cụm 2 quân liên tiếp (XX)
        if (count == 2) {
            // Tứ nhảy cách: XX.XX
            if (leftEmpty && leftIdx - 3 >= 0 && line[leftIdx - 2] == player && line[leftIdx - 3] == player) {
                if (!blockTwoEnds || !isBothEndsBlocked(line, leftIdx - 4, rightIdx + 1, opponent)) {
                    return PAT_DEAD_FOUR;
                }
            }
            if (rightEmpty && rightIdx + 3 <= 8 && line[rightIdx + 2] == player && line[rightIdx + 3] == player) {
                if (!blockTwoEnds || !isBothEndsBlocked(line, leftIdx - 1, rightIdx + 4, opponent)) {
                    return PAT_DEAD_FOUR;
                }
            }

            // Tam nhảy cách: .X.XX. hoặc .XX.X.
            if (leftEmpty && leftIdx - 2 >= 0 && line[leftIdx - 2] == player) {
                boolean farLeftEmpty = (leftIdx - 3 >= 0 && line[leftIdx - 3] == 0);
                if (farLeftEmpty && rightEmpty) return PAT_OPEN_THREE;
                if (farLeftEmpty || rightEmpty) return PAT_SLEEP_THREE;
            }
            if (rightEmpty && rightIdx + 2 <= 8 && line[rightIdx + 2] == player) {
                boolean farRightEmpty = (rightIdx + 3 <= 8 && line[rightIdx + 3] == 0);
                if (leftEmpty && farRightEmpty) return PAT_OPEN_THREE;
                if (leftEmpty || farRightEmpty) return PAT_SLEEP_THREE;
            }

            // Hai mở (..XX..) hoặc Hai ngủ (OXX...)
            if (leftEmpty && rightEmpty) {
                boolean leftOuter = (leftIdx - 2 >= 0 && line[leftIdx - 2] == 0);
                boolean rightOuter = (rightIdx + 2 <= 8 && line[rightIdx + 2] == 0);
                if (leftOuter || rightOuter) return PAT_OPEN_TWO;
                return PAT_SLEEP_TWO;
            }
            if ((leftEmpty && leftIdx - 2 >= 0 && line[leftIdx - 2] == 0) ||
                    (rightEmpty && rightIdx + 2 <= 8 && line[rightIdx + 2] == 0)) {
                return PAT_SLEEP_TWO;
            }
            return PAT_NONE;
        }

        // 5. Quân đơn lẻ (count == 1) nối với cụm phía xa
        if (count == 1) {
            if (leftEmpty && leftIdx - 4 >= 0 &&
                    line[leftIdx - 2] == player && line[leftIdx - 3] == player && line[leftIdx - 4] == player) {
                if (!blockTwoEnds || !isBothEndsBlocked(line, leftIdx - 5, rightIdx + 1, opponent)) {
                    return PAT_DEAD_FOUR;
                }
            }
            if (rightEmpty && rightIdx + 4 <= 8 &&
                    line[rightIdx + 2] == player && line[rightIdx + 3] == player && line[rightIdx + 4] == player) {
                if (!blockTwoEnds || !isBothEndsBlocked(line, leftIdx - 1, rightIdx + 5, opponent)) {
                    return PAT_DEAD_FOUR;
                }
            }

            if (leftEmpty && leftIdx - 3 >= 0 && line[leftIdx - 2] == player && line[leftIdx - 3] == player) {
                boolean farLeftEmpty = (leftIdx - 4 >= 0 && line[leftIdx - 4] == 0);
                if (farLeftEmpty && rightEmpty) return PAT_OPEN_THREE;
                if (farLeftEmpty || rightEmpty) return PAT_SLEEP_THREE;
            }
            if (rightEmpty && rightIdx + 3 <= 8 && line[rightIdx + 2] == player && line[rightIdx + 3] == player) {
                boolean farRightEmpty = (rightIdx + 4 <= 8 && line[rightIdx + 4] == 0);
                if (leftEmpty && farRightEmpty) return PAT_OPEN_THREE;
                if (leftEmpty || farRightEmpty) return PAT_SLEEP_THREE;
            }

            if (leftEmpty && leftIdx - 2 >= 0 && line[leftIdx - 2] == player) {
                boolean farLeftEmpty = (leftIdx - 3 >= 0 && line[leftIdx - 3] == 0);
                if (farLeftEmpty && rightEmpty) return PAT_OPEN_TWO;
                if (farLeftEmpty || rightEmpty) return PAT_SLEEP_TWO;
            }
            if (rightEmpty && rightIdx + 2 <= 8 && line[rightIdx + 2] == player) {
                boolean farRightEmpty = (rightIdx + 3 <= 8 && line[rightIdx + 3] == 0);
                if (leftEmpty && farRightEmpty) return PAT_OPEN_TWO;
                if (leftEmpty || farRightEmpty) return PAT_SLEEP_TWO;
            }
            if (leftEmpty && leftIdx - 3 >= 0 && line[leftIdx - 2] == 0 && line[leftIdx - 3] == player) {
                if (leftIdx - 4 >= 0 && line[leftIdx - 4] == 0 && rightEmpty) return PAT_OPEN_TWO;
            }
            if (rightEmpty && rightIdx + 3 <= 8 && line[rightIdx + 2] == 0 && line[rightIdx + 3] == player) {
                if (leftEmpty && rightIdx + 4 <= 8 && line[rightIdx + 4] == 0) return PAT_OPEN_TWO;
            }
        }

        return PAT_NONE;
    }

    private static boolean isBothEndsBlocked(int[] line, int leftOuterIdx, int rightOuterIdx, int opponent) {
        boolean leftBlocked = (leftOuterIdx < 0) || (line[leftOuterIdx] == opponent);
        boolean rightBlocked = (rightOuterIdx > 8) || (line[rightOuterIdx] == opponent);
        return leftBlocked && rightBlocked;
    }

    /**
     * Đánh giá tĩnh toàn bàn cờ tại nút lá (phiên bản không cấp phát bộ nhớ đệm mới).
     * Chỉ đánh giá duy nhất 1 lần tại quân trung tâm của mỗi chuỗi liên tiếp trên từng trục.
     */
    public static int evaluateGlobalBoard(int[][] board, int size, int aiPlayer, int nextTurnPlayer,
                                          boolean blockTwoEnds, int[] lineBuffer, PointThreat tempThreat) {
        long aiTotal = 0;
        long humanTotal = 0;

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int p = board[r][c];
                if (p == 0) continue;

                int opponent = (p == 1) ? 2 : 1;
                tempThreat.reset();

                for (int d = 0; d < 4; d++) {
                    int dr = DIRS[d][0];
                    int dc = DIRS[d][1];

                    // Bỏ qua nếu không phải ô đầu tiên của cụm liên tiếp trên trục (dr, dc)
                    int prevR = r - dr;
                    int prevC = c - dc;
                    if (prevR >= 0 && prevR < size && prevC >= 0 && prevC < size && board[prevR][prevC] == p) {
                        continue;
                    }

                    // Đếm độ dài cụm liên tiếp để lấy tâm cụm, đảm bảo cửa sổ 9 ô không bị lệch biên
                    int runLen = 1;
                    int nr = r + dr, nc = c + dc;
                    while (nr >= 0 && nr < size && nc >= 0 && nc < size && board[nr][nc] == p) {
                        runLen++;
                        nr += dr;
                        nc += dc;
                    }

                    int midOffset = Math.min(2, (runLen - 1) / 2);
                    int midR = r + midOffset * dr;
                    int midC = c + midOffset * dc;

                    extractWindow(board, size, midR, midC, dr, dc, p, opponent, lineBuffer);
                    int pat = classifyWindow(lineBuffer, p, opponent, blockTwoEnds);
                    accumulatePattern(tempThreat, pat);
                }

                if (tempThreat.isWin()) {
                    return (p == aiPlayer) ? SCORE_FIVE : -SCORE_FIVE;
                }

                int val = computeThreatScore(tempThreat);
                if (p == aiPlayer) {
                    aiTotal += val;
                } else {
                    humanTotal += val;
                }
            }
        }

        double defWeight = (nextTurnPlayer == aiPlayer) ? 1.12 : 1.25;
        double attWeight = (nextTurnPlayer == aiPlayer) ? 1.08 : 0.95;

        long diff = (long) (aiTotal * attWeight - humanTotal * defWeight);
        if (diff > SCORE_FIVE - 1000) diff = SCORE_FIVE - 1000;
        if (diff < -(SCORE_FIVE - 1000)) diff = -(SCORE_FIVE - 1000);
        return (int) diff;
    }

    public static int evaluateGlobalBoard(int[][] board, int size, int aiPlayer, int nextTurnPlayer, boolean blockTwoEnds) {
        return evaluateGlobalBoard(board, size, aiPlayer, nextTurnPlayer, blockTwoEnds, new int[9], new PointThreat());
    }
}