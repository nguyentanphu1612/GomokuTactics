package com.example.gomokutactics.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

import com.example.gomokutactics.models.BoardTheme;
import com.example.gomokutactics.models.Move;
import com.example.gomokutactics.utils.GamePrefs;

import java.util.ArrayList;
import java.util.List;

public class GomokuBoardView extends View {

    public interface OnCellClickListener {
        void onCellClick(int row, int col);
    }

    private int boardSize = 15;
    private int[][] board;
    private final List<Move> moveHistory = new ArrayList<>();
    private Move hintMove = null;
    private List<Move> winningLine = null;
    private boolean interactive = true;

    private OnCellClickListener cellClickListener;
    private BoardTheme currentTheme;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boardBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint p1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint p2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pieceShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint circleRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint circleHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lastMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint winLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF boardRect = new RectF();
    private final RectF cellRect = new RectF();

    // Zoom & Pan (Hỗ trợ bàn cờ lớn 20x20, 30x30)
    private float scaleFactor = 1.0f;
    private float translateX = 0f;
    private float translateY = 0f;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    // Animation
    private float lastPieceScale = 1.0f;
    private float hintPulseAlpha = 1.0f;
    private ValueAnimator hintAnimator;

    public GomokuBoardView(Context context) {
        super(context);
        init(context);
    }

    public GomokuBoardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GomokuBoardView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        currentTheme = GamePrefs.getCurrentTheme(context);
        boardSize = GamePrefs.getBoardSize(context);
        board = new int[boardSize][boardSize];

        boardBorderPaint.setStyle(Paint.Style.STROKE);
        boardBorderPaint.setStrokeWidth(4f);

        starPointPaint.setStyle(Paint.Style.FILL);

        pieceShadowPaint.setColor(Color.argb(48, 0, 0, 0));
        pieceShadowPaint.setStrokeCap(Paint.Cap.ROUND);

        circleRimPaint.setStyle(Paint.Style.STROKE);
        circleRimPaint.setStrokeWidth(2f);
        circleRimPaint.setColor(Color.argb(115, 100, 116, 139));

        circleHighlightPaint.setStyle(Paint.Style.FILL);
        circleHighlightPaint.setColor(Color.argb(75, 255, 255, 255));

        lastMovePaint.setStyle(Paint.Style.STROKE);
        lastMovePaint.setColor(Color.parseColor("#F59E0B"));
        lastMovePaint.setStrokeCap(Paint.Cap.ROUND);

        hintPaint.setStyle(Paint.Style.FILL);
        hintPaint.setColor(Color.parseColor("#10B981"));

        hintStrokePaint.setStyle(Paint.Style.STROKE);
        hintStrokePaint.setStrokeWidth(3f);
        hintStrokePaint.setColor(Color.parseColor("#059669"));

        winLinePaint.setStyle(Paint.Style.STROKE);
        winLinePaint.setColor(Color.parseColor("#EF4444"));
        winLinePaint.setStrokeCap(Paint.Cap.ROUND);

        applyThemePaints();

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 3.5f));
                clampTranslation();
                invalidate();
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (scaleFactor > 1.0f) {
                    translateX -= distanceX;
                    translateY -= distanceY;
                    clampTranslation();
                    invalidate();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (!interactive || cellClickListener == null) return false;
                handleTap(e.getX(), e.getY());
                return true;
            }
        });
    }

    public void applyThemePaints() {
        currentTheme = GamePrefs.getCurrentTheme(getContext());
        bgPaint.setColor(currentTheme.getBoardBgColor());

        int gridColor = currentTheme.getGridLineColor();
        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(2f);

        boardBorderPaint.setColor(gridColor);
        starPointPaint.setColor(gridColor);

        p1Paint.setColor(currentTheme.getP1Color());
        p1Paint.setStyle(currentTheme.isCirclePiece() ? Paint.Style.FILL : Paint.Style.STROKE);
        p1Paint.setStrokeCap(Paint.Cap.ROUND);

        p2Paint.setColor(currentTheme.getP2Color());
        p2Paint.setStyle(currentTheme.isCirclePiece() ? Paint.Style.FILL : Paint.Style.STROKE);
        p2Paint.setStrokeCap(Paint.Cap.ROUND);

        pieceShadowPaint.setStyle(currentTheme.isCirclePiece() ? Paint.Style.FILL : Paint.Style.STROKE);
        invalidate();
    }

    public void setBoardSize(int size) {
        this.boardSize = size;
        this.board = new int[size][size];
        this.moveHistory.clear();
        this.hintMove = null;
        this.winningLine = null;
        this.scaleFactor = 1.0f;
        this.translateX = 0f;
        this.translateY = 0f;
        invalidate();
    }

    public int getBoardSize() {
        return boardSize;
    }

    public int[][] getBoard() {
        return board;
    }

    public List<Move> getMoveHistory() {
        return moveHistory;
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    public void setOnCellClickListener(OnCellClickListener listener) {
        this.cellClickListener = listener;
    }

    public boolean placePiece(int row, int col, int player) {
        if (row < 0 || row >= boardSize || col < 0 || col >= boardSize) return false;
        board[row][col] = player;
        moveHistory.add(new Move(row, col, player));
        clearHint();
        startPiecePopAnimation();
        invalidate();
        return true;
    }

    public void undoLastMoves(int count) {
        for (int i = 0; i < count && !moveHistory.isEmpty(); i++) {
            Move last = moveHistory.remove(moveHistory.size() - 1);
            board[last.row][last.col] = 0;
        }
        winningLine = null;
        clearHint();
        invalidate();
    }

    public void setHintMove(Move hint) {
        this.hintMove = hint;
        if (hintAnimator != null) hintAnimator.cancel();
        hintAnimator = ValueAnimator.ofFloat(0.25f, 0.85f);
        hintAnimator.setDuration(600);
        hintAnimator.setRepeatCount(ValueAnimator.INFINITE);
        hintAnimator.setRepeatMode(ValueAnimator.REVERSE);
        hintAnimator.addUpdateListener(anim -> {
            hintPulseAlpha = (float) anim.getAnimatedValue();
            invalidate();
        });
        hintAnimator.start();
    }

    public void clearHint() {
        this.hintMove = null;
        if (hintAnimator != null) {
            hintAnimator.cancel();
            hintAnimator = null;
        }
        invalidate();
    }

    public void setWinningLine(List<Move> line) {
        this.winningLine = line;
        invalidate();
    }

    private void startPiecePopAnimation() {
        ValueAnimator animator = ValueAnimator.ofFloat(0.2f, 1.0f);
        animator.setDuration(220);
        animator.setInterpolator(new OvershootInterpolator());
        animator.addUpdateListener(animation -> {
            lastPieceScale = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void clampTranslation() {
        float viewW = getWidth();
        float viewH = getHeight();
        float maxTx = 0;
        float minTx = viewW - viewW * scaleFactor;
        float maxTy = 0;
        float minTy = viewH - viewH * scaleFactor;

        translateX = Math.max(minTx, Math.min(maxTx, translateX));
        translateY = Math.max(minTy, Math.min(maxTy, translateY));
    }

    private void handleTap(float touchX, float touchY) {
        float viewW = getWidth();
        float viewH = getHeight();
        float boardPx = Math.min(viewW, viewH);
        float offsetX = (viewW - boardPx) / 2f;
        float offsetY = (viewH - boardPx) / 2f;

        float unscaledX = (touchX - translateX) / scaleFactor;
        float unscaledY = (touchY - translateY) / scaleFactor;

        float cellSize = boardPx / boardSize;
        int col = (int) ((unscaledX - offsetX) / cellSize);
        int row = (int) ((unscaledY - offsetY) / cellSize);

        if (row >= 0 && row < boardSize && col >= 0 && col < boardSize && board[row][col] == 0) {
            cellClickListener.onCellClick(row, col);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float viewW = getWidth();
        float viewH = getHeight();
        float boardPx = Math.min(viewW, viewH);
        float offsetX = (viewW - boardPx) / 2f;
        float offsetY = (viewH - boardPx) / 2f;
        float cellSize = boardPx / boardSize;
        float cornerRadius = Math.min(24f, cellSize * 0.45f);

        canvas.save();
        canvas.translate(translateX, translateY);
        canvas.scale(scaleFactor, scaleFactor);

        // 1. Vẽ nền bàn cờ bo góc
        boardRect.set(offsetX, offsetY, offsetX + boardPx, offsetY + boardPx);
        canvas.drawRoundRect(boardRect, cornerRadius, cornerRadius, bgPaint);

        // 2. Vẽ lưới bàn cờ
        gridPaint.setStrokeWidth(boardSize >= 25 ? 1.2f : 1.8f);
        for (int i = 1; i < boardSize; i++) {
            float pos = i * cellSize;
            canvas.drawLine(offsetX + pos, offsetY, offsetX + pos, offsetY + boardPx, gridPaint);
            canvas.drawLine(offsetX, offsetY + pos, offsetX + boardPx, offsetY + pos, gridPaint);
        }

        // 3. Vẽ 5 chấm sao định vị (Hoshi) ở tâm và 4 góc đối xứng
        drawHoshiPoints(canvas, offsetX, offsetY, cellSize);

        // 4. Vẽ ô gợi ý Nhắc đường đi (nếu đang bật)
        if (hintMove != null && board[hintMove.row][hintMove.col] == 0) {
            hintPaint.setAlpha((int) (hintPulseAlpha * 170));
            hintStrokePaint.setAlpha((int) (hintPulseAlpha * 255));
            cellRect.set(
                    offsetX + hintMove.col * cellSize + 4,
                    offsetY + hintMove.row * cellSize + 4,
                    offsetX + (hintMove.col + 1) * cellSize - 4,
                    offsetY + (hintMove.row + 1) * cellSize - 4
            );
            float hintRadius = cellSize * 0.22f;
            canvas.drawRoundRect(cellRect, hintRadius, hintRadius, hintPaint);
            canvas.drawRoundRect(cellRect, hintRadius, hintRadius, hintStrokePaint);
        }

        // 5. Vẽ các quân cờ kèm đổ bóng & chiều sâu
        Move lastMove = moveHistory.isEmpty() ? null : moveHistory.get(moveHistory.size() - 1);
        float strokeW = Math.max(3f, cellSize * 0.14f);
        float shadowOffset = Math.max(2f, cellSize * 0.045f);
        p1Paint.setStrokeWidth(strokeW);
        p2Paint.setStrokeWidth(strokeW);
        pieceShadowPaint.setStrokeWidth(strokeW);

        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                int val = board[r][c];
                if (val == 0) continue;

                float cx = offsetX + (c + 0.5f) * cellSize;
                float cy = offsetY + (r + 0.5f) * cellSize;
                float radius = cellSize * 0.34f;

                if (lastMove != null && lastMove.row == r && lastMove.col == c) {
                    radius *= lastPieceScale;
                    drawLastMoveBrackets(canvas, offsetX, offsetY, cellSize, r, c);
                }

                if (currentTheme.isCirclePiece()) {
                    // Quân cờ tròn (Đen/Trắng - Lửa/Băng) có bóng đổ, viền và điểm sáng 3D
                    canvas.drawCircle(cx, cy + shadowOffset, radius, pieceShadowPaint);
                    canvas.drawCircle(cx, cy, radius, val == 1 ? p1Paint : p2Paint);
                    canvas.drawCircle(cx, cy, radius, circleRimPaint);
                    canvas.drawCircle(cx - radius * 0.28f, cy - radius * 0.28f, radius * 0.22f, circleHighlightPaint);
                } else {
                    if (val == 1) {
                        // Quân X + bóng đổ nhẹ
                        canvas.drawLine(cx - radius, cy - radius + shadowOffset, cx + radius, cy + radius + shadowOffset, pieceShadowPaint);
                        canvas.drawLine(cx + radius, cy - radius + shadowOffset, cx - radius, cy + radius + shadowOffset, pieceShadowPaint);
                        canvas.drawLine(cx - radius, cy - radius, cx + radius, cy + radius, p1Paint);
                        canvas.drawLine(cx + radius, cy - radius, cx - radius, cy + radius, p1Paint);
                    } else {
                        // Quân O + bóng đổ nhẹ
                        canvas.drawCircle(cx, cy + shadowOffset, radius, pieceShadowPaint);
                        canvas.drawCircle(cx, cy, radius, p2Paint);
                    }
                }
            }
        }

        // 6. Vẽ đường gạch nối 5 quân chiến thắng
        if (winningLine != null && winningLine.size() >= 2) {
            Move start = winningLine.get(0);
            Move end = winningLine.get(winningLine.size() - 1);
            winLinePaint.setStrokeWidth(Math.max(5f, cellSize * 0.16f));

            float startX = offsetX + (start.col + 0.5f) * cellSize;
            float startY = offsetY + (start.row + 0.5f) * cellSize;
            float endX = offsetX + (end.col + 0.5f) * cellSize;
            float endY = offsetY + (end.row + 0.5f) * cellSize;
            canvas.drawLine(startX, startY, endX, endY, winLinePaint);
        }

        // 7. Vẽ viền bao quanh bàn cờ
        canvas.drawRoundRect(boardRect, cornerRadius, cornerRadius, boardBorderPaint);

        canvas.restore();
    }

    private void drawHoshiPoints(Canvas canvas, float offsetX, float offsetY, float cellSize) {
        if (boardSize < 9) return;
        int center = boardSize / 2;
        int offset = (boardSize >= 20) ? 4 : 3;
        int[][] stars = {
                {center, center},
                {offset, offset},
                {offset, boardSize - 1 - offset},
                {boardSize - 1 - offset, offset},
                {boardSize - 1 - offset, boardSize - 1 - offset}
        };
        float dotRadius = Math.max(3f, cellSize * 0.09f);
        for (int[] star : stars) {
            float sx = offsetX + (star[1] + 0.5f) * cellSize;
            float sy = offsetY + (star[0] + 0.5f) * cellSize;
            canvas.drawCircle(sx, sy, dotRadius, starPointPaint);
        }
    }

    private void drawLastMoveBrackets(Canvas canvas, float offsetX, float offsetY, float cellSize, int r, int c) {
        float left = offsetX + c * cellSize + cellSize * 0.08f;
        float top = offsetY + r * cellSize + cellSize * 0.08f;
        float right = offsetX + (c + 1) * cellSize - cellSize * 0.08f;
        float bottom = offsetY + (r + 1) * cellSize - cellSize * 0.08f;
        float len = cellSize * 0.22f;

        lastMovePaint.setStrokeWidth(Math.max(2.5f, cellSize * 0.075f));

        canvas.drawLine(left, top, left + len, top, lastMovePaint);
        canvas.drawLine(left, top, left, top + len, lastMovePaint);

        canvas.drawLine(right, top, right - len, top, lastMovePaint);
        canvas.drawLine(right, top, right, top + len, lastMovePaint);

        canvas.drawLine(left, bottom, left + len, bottom, lastMovePaint);
        canvas.drawLine(left, bottom, left, bottom - len, lastMovePaint);

        canvas.drawLine(right, bottom, right - len, bottom, lastMovePaint);
        canvas.drawLine(right, bottom, right, bottom - len, lastMovePaint);
    }
}