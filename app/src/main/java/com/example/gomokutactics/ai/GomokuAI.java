package com.example.gomokutactics.ai;

import com.example.gomokutactics.models.Move;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Động cơ Trí tuệ nhân tạo Gomoku Grandmaster Engine:
 * - Cấp độ 1 (Dễ): Tìm kiếm nông + Sai số ngẫu nhiên nhẹ cho người mới tập chơi.
 * - Cấp độ 2 (Khó): Tìm kiếm Alpha-Beta độ sâu 4 + Nhận diện Tứ/Tam mở.
 * - Cấp độ 3 (Cực hạn - Bất khả chiến bại):
 *     1. Sách khai cuộc chuẩn thi đấu (Pro Opening Book)
 *     2. Lọc tình huống sinh tử tức thì (Immediate Win / Forced Block)
 *     3. Giải chuỗi chiếu bí ép buộc VCF (16 nước) & VCT (10 nước)
 *     4. Giải chuỗi phòng ngự VCF (Phát hiện bẫy của người chơi trước 14 nước để khóa chặt)
 *     5. Lặp sâu (Iterative Deepening) + Principal Variation Search (PVS) + Aspiration Windows
 *     6. Zobrist Transposition Table + Killer Heuristic + History Heuristic
 */
public class GomokuAI {

    private static final int INF = 50_000_000;
    private static final int MATE_SCORE = 9_000_000;

    private static final long TIME_BUDGET_EASY_MS = 250L;
    private static final long TIME_BUDGET_HARD_MS = 900L;
    private static final long TIME_BUDGET_EXTREME_MS = 2_600L;

    private static final Random RNG = new Random();

    private final int[][] board;
    private final int size;
    private final int aiPlayer;
    private final int humanPlayer;
    private final int difficulty;
    private final boolean blockTwoEnds;
    private final long deadlineNanos;

    private final ZobristTT tt;
    private final int[] lineBuffer = new int[9];
    private final PatternEvaluator.PointThreat aiThreatBuf = new PatternEvaluator.PointThreat();
    private final PatternEvaluator.PointThreat huThreatBuf = new PatternEvaluator.PointThreat();

    private boolean searchAborted = false;
    private int nodesSearched = 0;

    private GomokuAI(int[][] board, int size, int aiPlayer, int difficulty, boolean blockTwoEnds, long timeBudgetMs) {
        this.board = board;
        this.size = size;
        this.aiPlayer = aiPlayer;
        this.humanPlayer = (aiPlayer == 1) ? 2 : 1;
        this.difficulty = difficulty;
        this.blockTwoEnds = blockTwoEnds;
        this.deadlineNanos = System.nanoTime() + timeBudgetMs * 1_000_000L;
        this.tt = new ZobristTT();
    }

    public static Move findBestMove(int[][] board, int size, int aiPlayer, int difficulty, boolean blockTwoEnds) {
        long budget = (difficulty == 1) ? TIME_BUDGET_EASY_MS :
                (difficulty == 2) ? TIME_BUDGET_HARD_MS : TIME_BUDGET_EXTREME_MS;

        GomokuAI engine = new GomokuAI(board, size, aiPlayer, difficulty, blockTwoEnds, budget);
        return engine.computeOptimalMove();
    }

    public static Move getHintMove(int[][] board, int size, int player, boolean blockTwoEnds) {
        GomokuAI engine = new GomokuAI(board, size, player, 3, blockTwoEnds, 1_500L);
        return engine.computeOptimalMove();
    }

    private Move computeOptimalMove() {
        int totalStones = countStones();

        // TẦNG 1: SÁCH KHAI CUỘC (PRO OPENING BOOK)
        if (totalStones == 0) {
            return new Move(size / 2, size / 2, aiPlayer);
        }
        if (totalStones == 1 && difficulty >= 2) {
            Move openingReply = getOpeningBookReply();
            if (openingReply != null) return openingReply;
        }

        // TẦNG 2: KIỂM TRA NƯỚC THẮNG NGAY HOẶC NƯỚC CHẶN SINH TỬ BẮT BUỘC
        Move criticalMove = checkImmediateCriticalMoves();
        if (criticalMove != null) {
            return criticalMove;
        }

        // TẦNG 3: BỘ GIẢI CHUỖI ÉP THẮNG / ÉP PHÒNG THỦ (CHỈ DÀNH CHO CẤP ĐỘ CỰC HẠN)
        List<Integer> forcedDefenseFilter = null;
        if (difficulty == 3 && totalStones >= 5 && !isTimeUp()) {
            long solverDeadline = System.nanoTime() + 950_000_000L;
            ThreatSpaceSolver solver = new ThreatSpaceSolver(board, size, blockTwoEnds, solverDeadline);

            Move vcfWin = solver.findForcedWinMove(aiPlayer, 16, false);
            if (vcfWin != null) {
                return vcfWin;
            }

            Move vctWin = solver.findForcedWinMove(aiPlayer, 10, true);
            if (vctWin != null) {
                return vctWin;
            }

            forcedDefenseFilter = solver.findDefensiveMovesAgainstOpponentVCF(aiPlayer, 14);
            if (forcedDefenseFilter != null && forcedDefenseFilter.size() == 1) {
                int onlyDefense = forcedDefenseFilter.get(0);
                int r = ZobristTT.unpackRow(onlyDefense);
                int c = ZobristTT.unpackCol(onlyDefense);
                if (isValidEmptyCell(r, c)) {
                    return new Move(r, c, aiPlayer);
                }
            }
        }

        // TẦNG 4: TÌM KIẾM SÂU ITERATIVE DEEPENING + PVS + ASPIRATION WINDOWS
        return runIterativeDeepeningPVS(forcedDefenseFilter);
    }

    private Move getOpeningBookReply() {
        int firstR = -1, firstC = -1;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board[r][c] != 0) {
                    firstR = r;
                    firstC = c;
                    break;
                }
            }
            if (firstR != -1) break;
        }
        if (firstR == -1) return new Move(size / 2, size / 2, aiPlayer);

        int center = size / 2;
        if (Math.abs(firstR - center) > 2 || Math.abs(firstC - center) > 2) {
            if (board[center][center] == 0) {
                return new Move(center, center, aiPlayer);
            }
        }

        int[][] diagOffsets = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        int bestR = -1, bestC = -1;
        int bestCenterDist = Integer.MAX_VALUE;

        for (int[] off : diagOffsets) {
            int nr = firstR + off[0];
            int nc = firstC + off[1];
            if (nr >= 1 && nr < size - 1 && nc >= 1 && nc < size - 1 && board[nr][nc] == 0) {
                int dist = Math.abs(nr - center) + Math.abs(nc - center);
                if (dist < bestCenterDist) {
                    bestCenterDist = dist;
                    bestR = nr;
                    bestC = nc;
                }
            }
        }
        if (bestR != -1) {
            return new Move(bestR, bestC, aiPlayer);
        }
        return null;
    }

    private Move checkImmediateCriticalMoves() {
        int winR = -1, winC = -1;
        int blockFiveR = -1, blockFiveC = -1;
        int openFourR = -1, openFourC = -1;
        int bestBlockScore = -1;

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board[r][c] != 0 || !hasNeighborWithin(r, c, 2)) continue;

                PatternEvaluator.evaluatePoint(board, size, r, c, aiPlayer, blockTwoEnds, lineBuffer, aiThreatBuf);
                if (aiThreatBuf.isWin()) {
                    winR = r;
                    winC = c;
                    break;
                }

                PatternEvaluator.evaluatePoint(board, size, r, c, humanPlayer, blockTwoEnds, lineBuffer, huThreatBuf);
                if (huThreatBuf.isWin()) {
                    int comboScore = aiThreatBuf.totalScore + huThreatBuf.totalScore;
                    if (comboScore > bestBlockScore) {
                        bestBlockScore = comboScore;
                        blockFiveR = r;
                        blockFiveC = c;
                    }
                }

                if (aiThreatBuf.openFourCount > 0 && openFourR == -1) {
                    openFourR = r;
                    openFourC = c;
                }
            }
            if (winR != -1) break;
        }

        if (winR != -1) return new Move(winR, winC, aiPlayer);
        if (blockFiveR != -1) return new Move(blockFiveR, blockFiveC, aiPlayer);
        if (openFourR != -1 && difficulty >= 2) return new Move(openFourR, openFourC, aiPlayer);

        return null;
    }

    private Move runIterativeDeepeningPVS(List<Integer> forcedDefenseFilter) {
        List<CandidateMove> rootCandidates = generateOrderedMoves(aiPlayer, 0, (short) -1);
        if (forcedDefenseFilter != null && !forcedDefenseFilter.isEmpty()) {
            List<CandidateMove> filtered = new ArrayList<>();
            for (CandidateMove cm : rootCandidates) {
                if (forcedDefenseFilter.contains((int) ZobristTT.packMove(cm.r, cm.c))) {
                    filtered.add(cm);
                }
            }
            if (!filtered.isEmpty()) {
                rootCandidates = filtered;
            }
        }

        if (rootCandidates.isEmpty()) {
            return findFallbackCenterMove();
        }
        if (rootCandidates.size() == 1) {
            CandidateMove only = rootCandidates.get(0);
            return new Move(only.r, only.c, aiPlayer);
        }

        if (difficulty == 1) {
            int pickRange = Math.min(3, rootCandidates.size());
            CandidateMove chosen = rootCandidates.get(RNG.nextInt(pickRange));
            return new Move(chosen.r, chosen.c, aiPlayer);
        }

        int maxTargetDepth = (difficulty == 2) ? 4 : 10;
        int rootBranchLimit = (difficulty == 2) ? 10 : (size >= 25 ? 12 : 16);
        if (rootCandidates.size() > rootBranchLimit) {
            rootCandidates = new ArrayList<>(rootCandidates.subList(0, rootBranchLimit));
        }

        CandidateMove bestOverallMove = rootCandidates.get(0);
        int bestOverallScore = -INF;
        long rootHash = ZobristTT.computeBoardHash(board, size, aiPlayer);

        for (int currentDepth = 2; currentDepth <= maxTargetDepth; currentDepth++) {
            if (isTimeUp()) break;

            searchAborted = false;
            int alpha = -INF;
            int beta = INF;

            if (currentDepth >= 4 && Math.abs(bestOverallScore) < MATE_SCORE - 1000) {
                alpha = bestOverallScore - 35_000;
                beta = bestOverallScore + 35_000;
            }

            CandidateMove iterationBestMove;
            int iterationBestScore;

            while (true) {
                int localAlpha = alpha;
                iterationBestScore = -INF;
                iterationBestMove = null;

                for (int i = 0; i < rootCandidates.size(); i++) {
                    CandidateMove cm = rootCandidates.get(i);
                    board[cm.r][cm.c] = aiPlayer;
                    long nextHash = ZobristTT.updateHash(rootHash, cm.r, cm.c, aiPlayer);

                    int score;
                    if (i == 0) {
                        score = -pvs(currentDepth - 1, -beta, -localAlpha, humanPlayer, 1, nextHash);
                    } else {
                        score = -pvs(currentDepth - 1, -localAlpha - 1, -localAlpha, humanPlayer, 1, nextHash);
                        if (!searchAborted && score > localAlpha && score < beta) {
                            score = -pvs(currentDepth - 1, -beta, -localAlpha, humanPlayer, 1, nextHash);
                        }
                    }

                    board[cm.r][cm.c] = 0;

                    if (searchAborted) break;

                    cm.searchScore = score;
                    if (score > iterationBestScore) {
                        iterationBestScore = score;
                        iterationBestMove = cm;
                    }
                    if (score > localAlpha) {
                        localAlpha = score;
                    }
                }

                if (searchAborted) break;

                if (iterationBestScore <= alpha) {
                    alpha = -INF;
                } else if (iterationBestScore >= beta) {
                    beta = INF;
                } else {
                    break;
                }
            }

            if (!searchAborted && iterationBestMove != null) {
                bestOverallMove = iterationBestMove;
                bestOverallScore = iterationBestScore;
                rootCandidates.sort((a, b) -> Integer.compare(b.searchScore, a.searchScore));

                if (bestOverallScore >= MATE_SCORE - 50) {
                    break;
                }
            } else {
                break;
            }
        }

        return new Move(bestOverallMove.r, bestOverallMove.c, aiPlayer);
    }

    private int pvs(int depth, int alpha, int beta, int currentPlayer, int ply, long hash) {
        nodesSearched++;
        if ((nodesSearched & 2047) == 0 && isTimeUp()) {
            searchAborted = true;
            return 0;
        }

        int origAlpha = alpha;
        short ttMovePacked = -1;

        int ttIdx = tt.probeIndex(hash);
        if (ttIdx != -1) {
            ttMovePacked = tt.getBestMove(ttIdx);
            if (tt.getDepth(ttIdx) >= depth) {
                int ttVal = tt.getScore(ttIdx);
                if (ttVal > MATE_SCORE - 100) ttVal -= ply;
                else if (ttVal < -(MATE_SCORE - 100)) ttVal += ply;

                byte flag = tt.getFlag(ttIdx);
                if (flag == ZobristTT.FLAG_EXACT) return ttVal;
                if (flag == ZobristTT.FLAG_LOWERBOUND && ttVal > alpha) alpha = ttVal;
                else if (flag == ZobristTT.FLAG_UPPERBOUND && ttVal < beta) beta = ttVal;

                if (alpha >= beta) return ttVal;
            }
        }

        if (depth <= 0) {
            int eval = PatternEvaluator.evaluateGlobalBoard(
                    board, size, aiPlayer, currentPlayer, blockTwoEnds, lineBuffer, aiThreatBuf
            );
            return (currentPlayer == aiPlayer) ? eval : -eval;
        }

        List<CandidateMove> moves = generateOrderedMoves(currentPlayer, ply, ttMovePacked);
        if (moves.isEmpty()) {
            return 0;
        }

        int branchCap = (difficulty == 2) ? 8 : (ply <= 2 ? 12 : (ply <= 4 ? 9 : 7));
        if (moves.size() > branchCap && !moves.get(0).isForcedResponse) {
            moves = moves.subList(0, branchCap);
        }

        int opponent = (currentPlayer == 1) ? 2 : 1;
        int bestScore = -INF;
        int bestR = -1, bestC = -1;

        for (int i = 0; i < moves.size(); i++) {
            CandidateMove m = moves.get(i);

            if (m.isImmediateWin) {
                int winScore = MATE_SCORE - ply;
                tt.store(hash, depth, winScore, ZobristTT.FLAG_EXACT, m.r, m.c);
                return winScore;
            }

            board[m.r][m.c] = currentPlayer;
            long nextHash = ZobristTT.updateHash(hash, m.r, m.c, currentPlayer);

            int score;
            if (i == 0) {
                score = -pvs(depth - 1, -beta, -alpha, opponent, ply + 1, nextHash);
            } else {
                score = -pvs(depth - 1, -alpha - 1, -alpha, opponent, ply + 1, nextHash);
                if (!searchAborted && score > alpha && score < beta) {
                    score = -pvs(depth - 1, -beta, -alpha, opponent, ply + 1, nextHash);
                }
            }

            board[m.r][m.c] = 0;

            if (searchAborted) return 0;

            if (score > bestScore) {
                bestScore = score;
                bestR = m.r;
                bestC = m.c;
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                tt.recordKiller(ply, m.r, m.c);
                tt.recordHistory(currentPlayer, m.r, m.c, depth);
                break;
            }
        }

        byte storeFlag;
        if (bestScore <= origAlpha) {
            storeFlag = ZobristTT.FLAG_UPPERBOUND;
        } else if (bestScore >= beta) {
            storeFlag = ZobristTT.FLAG_LOWERBOUND;
        } else {
            storeFlag = ZobristTT.FLAG_EXACT;
        }

        int ttStoreScore = bestScore;
        if (ttStoreScore > MATE_SCORE - 100) ttStoreScore += ply;
        else if (ttStoreScore < -(MATE_SCORE - 100)) ttStoreScore -= ply;

        tt.store(hash, depth, ttStoreScore, storeFlag, bestR, bestC);
        return bestScore;
    }

    private List<CandidateMove> generateOrderedMoves(int currentPlayer, int ply, short ttMovePacked) {
        int opponent = (currentPlayer == 1) ? 2 : 1;
        List<CandidateMove> allMoves = new ArrayList<>();
        List<CandidateMove> winMoves = new ArrayList<>();
        List<CandidateMove> blockFourMoves = new ArrayList<>();
        List<CandidateMove> openThreeDefenseAndCounters = new ArrayList<>();

        boolean opponentHasFour = false;
        boolean opponentHasOpenThree = false;
        int center = size / 2;

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board[r][c] != 0 || !hasNeighborWithin(r, c, 2)) continue;

                PatternEvaluator.evaluatePoint(board, size, r, c, currentPlayer, blockTwoEnds, lineBuffer, aiThreatBuf);
                PatternEvaluator.evaluatePoint(board, size, r, c, opponent, blockTwoEnds, lineBuffer, huThreatBuf);

                int centerProximityBonus = Math.max(0, 30 - (Math.abs(r - center) + Math.abs(c - center)) * 2);
                int ttBonus = (ttMovePacked != -1 && ZobristTT.packMove(r, c) == ttMovePacked) ? 500_000 : 0;
                int killerBonus = tt.getKillerBonus(ply, r, c);
                int histBonus = tt.getHistoryScore(currentPlayer, r, c);

                int orderScore = aiThreatBuf.totalScore + (int) (huThreatBuf.totalScore * 1.15)
                        + ttBonus + killerBonus + histBonus + centerProximityBonus;

                CandidateMove cm = new CandidateMove(r, c, orderScore);

                if (aiThreatBuf.isWin()) {
                    cm.isImmediateWin = true;
                    cm.isForcedResponse = true;
                    winMoves.add(cm);
                }

                if (huThreatBuf.isWin()) {
                    opponentHasFour = true;
                    cm.isForcedResponse = true;
                    blockFourMoves.add(cm);
                } else if (huThreatBuf.isLethalOpenFour()) {
                    opponentHasOpenThree = true;
                    cm.isForcedResponse = true;
                    openThreeDefenseAndCounters.add(cm);
                } else if (aiThreatBuf.hasFourThreat()) {
                    openThreeDefenseAndCounters.add(cm);
                }

                allMoves.add(cm);
            }
        }

        List<CandidateMove> targetList;
        if (!winMoves.isEmpty()) {
            targetList = winMoves;
        } else if (opponentHasFour) {
            targetList = blockFourMoves;
        } else if (opponentHasOpenThree && !openThreeDefenseAndCounters.isEmpty()) {
            targetList = openThreeDefenseAndCounters;
        } else {
            targetList = allMoves;
        }

        targetList.sort((a, b) -> Integer.compare(b.orderScore, a.orderScore));
        return targetList;
    }

    private boolean isValidEmptyCell(int r, int c) {
        return r >= 0 && r < size && c >= 0 && c < size && board[r][c] == 0;
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

    private int countStones() {
        int count = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board[r][c] != 0) count++;
            }
        }
        return count;
    }

    private Move findFallbackCenterMove() {
        int center = size / 2;
        if (board[center][center] == 0) return new Move(center, center, aiPlayer);
        for (int radius = 1; radius < size; radius++) {
            for (int r = Math.max(0, center - radius); r <= Math.min(size - 1, center + radius); r++) {
                for (int c = Math.max(0, center - radius); c <= Math.min(size - 1, center + radius); c++) {
                    if (board[r][c] == 0) return new Move(r, c, aiPlayer);
                }
            }
        }
        return new Move(0, 0, aiPlayer);
    }

    private boolean isTimeUp() {
        return System.nanoTime() > deadlineNanos || Thread.currentThread().isInterrupted();
    }

    private static class CandidateMove {
        final int r;
        final int c;
        final int orderScore;
        int searchScore;
        boolean isImmediateWin = false;
        boolean isForcedResponse = false;

        CandidateMove(int r, int c, int orderScore) {
            this.r = r;
            this.c = c;
            this.orderScore = orderScore;
            this.searchScore = orderScore;
        }
    }
}