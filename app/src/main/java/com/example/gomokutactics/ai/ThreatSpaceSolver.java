package com.example.gomokutactics.ai;

import com.example.gomokutactics.models.Move;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bộ giải không gian đe dọa chuyên sâu (Threat-Space Search Engine):
 * 1. VCF (Victory by Continuous Four): Tìm chuỗi ép Tứ liên tục tới 16 bước.
 * 2. VCT (Victory by Continuous Threat): Tìm chuỗi ép Tứ + Tam mở tới 10 bước.
 * 3. Defensive Threat Check: Phát hiện trước các chuỗi thắng của người chơi để chặn từ gốc.
 */
public class ThreatSpaceSolver {

    private static final int MAX_MEMO_ENTRIES = 32_768;

    private final int[][] board;
    private final int size;
    private final boolean blockTwoEnds;
    private final long deadlineNanos;
    private final Map<Long, Boolean> vcfMemo = new HashMap<>(4096);
    private final int[] lineBuffer = new int[9];
    private final PatternEvaluator.PointThreat tempThreat = new PatternEvaluator.PointThreat();

    public ThreatSpaceSolver(int[][] board, int size, boolean blockTwoEnds, long deadlineNanos) {
        this.board = board;
        this.size = size;
        this.blockTwoEnds = blockTwoEnds;
        this.deadlineNanos = deadlineNanos;
    }

    public boolean isTimedOut() {
        return System.nanoTime() > deadlineNanos || Thread.currentThread().isInterrupted();
    }

    /**
     * Tìm nước đi mở đầu cho chuỗi thắng ép buộc VCF hoặc VCT.
     */
    public Move findForcedWinMove(int attacker, int maxDepth, boolean allowOpenThree) {
        if (isTimedOut()) return null;

        vcfMemo.clear();
        int defender = (attacker == 1) ? 2 : 1;
        long rootHash = ZobristTT.computeBoardHash(board, size, attacker);

        List<Integer> urgentBlocks = findImmediateWinningSquares(defender);
        if (urgentBlocks.size() >= 2) {
            List<Integer> myWins = findImmediateWinningSquares(attacker);
            if (!myWins.isEmpty()) {
                int packed = myWins.get(0);
                return new Move(ZobristTT.unpackRow(packed), ZobristTT.unpackCol(packed), attacker);
            }
            return null;
        }

        List<ScoredSquare> candidates = generateAttackMoves(attacker, allowOpenThree);
        if (!urgentBlocks.isEmpty()) {
            int mustBlock = urgentBlocks.get(0);
            List<ScoredSquare> filtered = new ArrayList<>();
            for (ScoredSquare sq : candidates) {
                if (sq.isImmediateWin || sq.packed == mustBlock) {
                    filtered.add(sq);
                }
            }
            candidates = filtered;
        }

        for (ScoredSquare sq : candidates) {
            if (isTimedOut()) break;

            int r = ZobristTT.unpackRow(sq.packed);
            int c = ZobristTT.unpackCol(sq.packed);

            if (sq.isImmediateWin) {
                return new Move(r, c, attacker);
            }

            board[r][c] = attacker;
            long nextHash = ZobristTT.updateHash(rootHash, r, c, attacker);
            boolean defenderSurvived = canDefenderSurvive(attacker, defender, r, c, sq.isOpenFour, maxDepth - 1, allowOpenThree, nextHash);
            board[r][c] = 0;

            if (!isTimedOut() && !defenderSurvived) {
                return new Move(r, c, attacker);
            }
        }
        return null;
    }

    /**
     * Kiểm tra xem người chơi có đang sở hữu chuỗi VCF thắng ép buộc hay không.
     * Nếu có, tìm các nước đi của AI thực sự triệt tiêu được chuỗi VCF đó.
     */
    public List<Integer> findDefensiveMovesAgainstOpponentVCF(int aiPlayer, int maxDepth) {
        int human = (aiPlayer == 1) ? 2 : 1;
        Move humanWinStart = findForcedWinMove(human, maxDepth, false);
        if (humanWinStart == null || isTimedOut()) {
            return null;
        }

        List<Integer> validDefenses = new ArrayList<>();
        List<ScoredSquare> defenseCandidates = generateCandidateDefenses(aiPlayer, human);

        for (ScoredSquare defSq : defenseCandidates) {
            if (isTimedOut()) break;

            int r = ZobristTT.unpackRow(defSq.packed);
            int c = ZobristTT.unpackCol(defSq.packed);

            board[r][c] = aiPlayer;
            Move stillHasWin = findForcedWinMove(human, maxDepth - 2, false);
            board[r][c] = 0;

            // Chỉ công nhận hóa giải được nếu quá trình kiểm tra hoàn tất trước khi hết giờ
            if (!isTimedOut() && stillHasWin == null) {
                validDefenses.add(defSq.packed);
                if (validDefenses.size() >= 4) break;
            }
        }

        int startPacked = ZobristTT.packMove(humanWinStart.row, humanWinStart.col);
        if (!validDefenses.contains(startPacked)) {
            validDefenses.add(0, startPacked);
        }
        return validDefenses;
    }

    private boolean attackerTurn(int attacker, int defender, int depth, boolean allowOpenThree, long hash) {
        if (depth <= 0 || isTimedOut()) return false;

        Boolean cached = vcfMemo.get(hash);
        if (cached != null) return cached;

        List<Integer> urgentBlocks = findImmediateWinningSquares(defender);
        if (urgentBlocks.size() >= 2) {
            List<Integer> myWins = findImmediateWinningSquares(attacker);
            boolean winNow = !myWins.isEmpty();
            putMemo(hash, winNow);
            return winNow;
        }

        List<ScoredSquare> attacks = generateAttackMoves(attacker, allowOpenThree);
        if (!urgentBlocks.isEmpty()) {
            int mustBlock = urgentBlocks.get(0);
            List<ScoredSquare> filtered = new ArrayList<>();
            for (ScoredSquare sq : attacks) {
                if (sq.isImmediateWin || sq.packed == mustBlock) {
                    filtered.add(sq);
                }
            }
            attacks = filtered;
        }

        for (ScoredSquare sq : attacks) {
            if (isTimedOut()) return false;
            if (sq.isImmediateWin) {
                putMemo(hash, true);
                return true;
            }

            int r = ZobristTT.unpackRow(sq.packed);
            int c = ZobristTT.unpackCol(sq.packed);

            board[r][c] = attacker;
            long nextHash = ZobristTT.updateHash(hash, r, c, attacker);
            boolean defenderSurvived = canDefenderSurvive(attacker, defender, r, c, sq.isOpenFour, depth - 1, allowOpenThree, nextHash);
            board[r][c] = 0;

            if (!isTimedOut() && !defenderSurvived) {
                putMemo(hash, true);
                return true;
            }
        }

        if (!isTimedOut()) {
            putMemo(hash, false);
        }
        return false;
    }

    private void putMemo(long hash, boolean result) {
        if (vcfMemo.size() >= MAX_MEMO_ENTRIES) {
            vcfMemo.clear();
        }
        vcfMemo.put(hash, result);
    }

    private boolean canDefenderSurvive(int attacker, int defender, int lastAttR, int lastAttC,
                                       boolean wasOpenFour, int depth, boolean allowOpenThree, long hash) {
        if (wasOpenFour) {
            return !findImmediateWinningSquares(defender).isEmpty();
        }

        List<Integer> responses = findDefenseResponsesForLastMove(attacker, defender, lastAttR, lastAttC, allowOpenThree);

        if (responses.isEmpty()) {
            return false;
        }
        if (depth <= 0 || isTimedOut()) {
            return true;
        }

        for (int packed : responses) {
            int r = ZobristTT.unpackRow(packed);
            int c = ZobristTT.unpackCol(packed);

            if (PatternEvaluator.isWinningMove(board, size, r, c, defender, blockTwoEnds)) {
                return true;
            }

            board[r][c] = defender;
            long nextHash = ZobristTT.updateHash(hash, r, c, defender);
            boolean attackerStillWins = attackerTurn(attacker, defender, depth - 1, allowOpenThree, nextHash);
            board[r][c] = 0;

            if (!attackerStillWins) {
                return true;
            }
        }

        return false;
    }

    public List<Integer> findImmediateWinningSquares(int player) {
        List<Integer> wins = new ArrayList<>();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board[r][c] == 0 && hasNeighborWithin(r, c, 2)) {
                    if (PatternEvaluator.isWinningMove(board, size, r, c, player, blockTwoEnds)) {
                        wins.add((int) ZobristTT.packMove(r, c));
                    }
                }
            }
        }
        return wins;
    }

    private List<ScoredSquare> generateAttackMoves(int attacker, boolean allowOpenThree) {
        List<ScoredSquare> list = new ArrayList<>();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board[r][c] != 0 || !hasNeighborWithin(r, c, 2)) continue;

                PatternEvaluator.evaluatePoint(board, size, r, c, attacker, blockTwoEnds, lineBuffer, tempThreat);
                if (tempThreat.isWin()) {
                    list.add(0, new ScoredSquare(ZobristTT.packMove(r, c), PatternEvaluator.SCORE_FIVE, true, true));
                } else if (tempThreat.isLethalOpenFour()) {
                    list.add(new ScoredSquare(ZobristTT.packMove(r, c), tempThreat.totalScore, false, true));
                } else if (tempThreat.deadFourCount > 0) {
                    list.add(new ScoredSquare(ZobristTT.packMove(r, c), tempThreat.totalScore, false, false));
                } else if (allowOpenThree && tempThreat.openThreeCount > 0) {
                    list.add(new ScoredSquare(ZobristTT.packMove(r, c), tempThreat.totalScore, false, false));
                }
            }
        }
        list.sort((a, b) -> Integer.compare(b.score, a.score));
        if (list.size() > 14) {
            return list.subList(0, 14);
        }
        return list;
    }

    private List<Integer> findDefenseResponsesForLastMove(int attacker, int defender, int attR, int attC, boolean allowOpenThree) {
        List<Integer> responses = new ArrayList<>();
        responses.addAll(findImmediateWinningSquares(defender));

        for (int[] dir : PatternEvaluator.DIRS) {
            for (int step = -4; step <= 4; step++) {
                if (step == 0) continue;
                int nr = attR + step * dir[0];
                int nc = attC + step * dir[1];
                if (nr < 0 || nr >= size || nc < 0 || nc >= size || board[nr][nc] != 0) continue;

                int packed = ZobristTT.packMove(nr, nc);
                if (responses.contains(packed)) continue;

                PatternEvaluator.evaluatePoint(board, size, nr, nc, attacker, blockTwoEnds, lineBuffer, tempThreat);
                if (tempThreat.isWin()) {
                    responses.add(packed);
                } else if (allowOpenThree && (tempThreat.openFourCount > 0 || tempThreat.deadFourCount >= 2)) {
                    responses.add(packed);
                }
            }
        }

        if (allowOpenThree) {
            for (int r = 0; r < size; r++) {
                for (int c = 0; c < size; c++) {
                    if (board[r][c] != 0 || !hasNeighborWithin(r, c, 2)) continue;
                    int packed = ZobristTT.packMove(r, c);
                    if (responses.contains(packed)) continue;

                    PatternEvaluator.evaluatePoint(board, size, r, c, defender, blockTwoEnds, lineBuffer, tempThreat);
                    if (tempThreat.hasFourThreat()) {
                        responses.add(packed);
                    }
                }
            }
        }

        return responses;
    }

    private List<ScoredSquare> generateCandidateDefenses(int aiPlayer, int human) {
        List<ScoredSquare> list = new ArrayList<>();
        PatternEvaluator.PointThreat humanThreat = new PatternEvaluator.PointThreat();

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board[r][c] != 0 || !hasNeighborWithin(r, c, 2)) continue;

                PatternEvaluator.evaluatePoint(board, size, r, c, aiPlayer, blockTwoEnds, lineBuffer, tempThreat);
                PatternEvaluator.evaluatePoint(board, size, r, c, human, blockTwoEnds, lineBuffer, humanThreat);

                int combined = tempThreat.totalScore + (int) (humanThreat.totalScore * 1.25);
                if (combined > 500) {
                    list.add(new ScoredSquare(ZobristTT.packMove(r, c), combined, tempThreat.isWin(), tempThreat.isLethalOpenFour()));
                }
            }
        }
        list.sort((a, b) -> Integer.compare(b.score, a.score));
        return list.size() > 12 ? list.subList(0, 12) : list;
    }

    private boolean hasNeighborWithin(int r, int c, int radius) {
        int rMin = Math.max(0, r - radius);
        int rMax = Math.min(size - 1, r + radius);
        int cMin = Math.max(0, c - radius);
        int cMax = Math.min(size - 1, c + radius);

        for (int i = rMin; i <= rMax; i++) {
            for (int j = cMin; j <= cMax; j++) {
                if ((i != r || j != c) && board[i][j] != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static class ScoredSquare {
        final int packed;
        final int score;
        final boolean isImmediateWin;
        final boolean isOpenFour;

        ScoredSquare(int packed, int score, boolean isImmediateWin, boolean isOpenFour) {
            this.packed = packed;
            this.score = score;
            this.isImmediateWin = isImmediateWin;
            this.isOpenFour = isOpenFour;
        }
    }
}