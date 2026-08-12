package com.yukkurimatchatea.nxdanmakutv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public final class DanmakuView extends View {
    private static final long FIXED_DURATION_MS = 5000L;
    private static final long STATUS_DURATION_MS = 3000L;
    private static final long EVENT_DURATION_MS = 12000L;
    private static final int MAX_COMMENTS = 80;
    private static final int MAX_EVENTS = 4;

    private enum Position { SCROLL, TOP, BOTTOM }

    private static final class Item {
        final String text;
        final int color;
        final float textSize;
        final Position position;
        final long createdAt;
        final int lane;
        float measuredWidth;

        Item(String text, int color, float textSize, Position position, int lane) {
            this.text = text;
            this.color = color;
            this.textSize = textSize;
            this.position = position;
            this.createdAt = SystemClock.uptimeMillis();
            this.lane = lane;
        }
    }

    private static final class EventItem {
        final String text;
        final long createdAt = SystemClock.uptimeMillis();

        EventItem(String text) {
            this.text = text;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final List<EventItem> events = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int nextScrollLane;
    private int nextTopLane;
    private int nextBottomLane;
    private String channelLabel = "";
    private String status = "待機中";
    private float scale = 1.0f;
    private long scrollDurationMs = 6000L;
    private int commentAlpha = 230;
    private int densityPercent = 100;
    private int densityAccumulator;
    private float verticalCoverage = 0.90f;
    private float outlineRatio = 0.075f;
    private boolean statusEnabled = true;
    private boolean eventLogEnabled = true;
    private boolean statusShown;
    private boolean overlayActive = true;
    private final Runnable hideStatus = () -> {
        statusShown = false;
        invalidate();
    };

    public DanmakuView(Context context) {
        super(context);
        initialize();
    }

    public DanmakuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    private void initialize() {
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        badgePaint.setTextSize(dp(16));
        badgePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    }

    public void setTextScale(float scale) {
        this.scale = Math.max(0.6f, Math.min(1.8f, scale));
    }

    public void applyDisplaySettings(AppPreferences preferences) {
        setTextScale(preferences.textScale());
        scrollDurationMs = Math.max(3000L,
                Math.min(10000L, preferences.scrollDurationSeconds() * 1000L));
        commentAlpha = Math.round(255f * Math.max(30,
                Math.min(100, preferences.opacityPercent())) / 100f);
        densityPercent = Math.max(25, Math.min(100, preferences.densityPercent()));
        densityAccumulator = 0;
        verticalCoverage = Math.max(0.50f,
                Math.min(1.0f, preferences.verticalCoveragePercent() / 100f));
        outlineRatio = switch (preferences.outlineLevel()) {
            case 1 -> 0.045f;
            case 3 -> 0.11f;
            default -> 0.075f;
        };
        postInvalidateOnAnimation();
    }

    public void setChannel(String label) {
        channelLabel = label == null ? "" : label;
        clearComments();
        invalidate();
    }

    public void setStatus(String value) {
        status = value == null ? "" : value;
        removeCallbacks(hideStatus);
        statusShown = statusEnabled && !status.isEmpty();
        postInvalidateOnAnimation();
    }

    public void setConnectedStatus(String value) {
        status = value == null ? "" : value;
        removeCallbacks(hideStatus);
        statusShown = statusEnabled && !status.isEmpty();
        if (statusShown) {
            postDelayed(hideStatus, STATUS_DURATION_MS);
        }
        postInvalidateOnAnimation();
    }

    public void setStatusVisible(boolean visible) {
        if (statusEnabled == visible) {
            return;
        }
        statusEnabled = visible;
        removeCallbacks(hideStatus);
        statusShown = visible && !status.isEmpty();
        if (statusShown) {
            postDelayed(hideStatus, STATUS_DURATION_MS);
        }
        postInvalidateOnAnimation();
    }

    public void setEventLogVisible(boolean visible) {
        eventLogEnabled = visible;
        if (!visible) {
            events.clear();
        }
        postInvalidateOnAnimation();
    }

    public void addEvent(String value) {
        if (!eventLogEnabled || value == null) {
            return;
        }
        String sanitized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (sanitized.isEmpty()) {
            return;
        }
        if (sanitized.length() > 72) {
            sanitized = sanitized.substring(0, 72) + "…";
        }
        if (!events.isEmpty() && events.get(events.size() - 1).text.equals(sanitized)) {
            return;
        }
        while (events.size() >= MAX_EVENTS) {
            events.remove(0);
        }
        events.add(new EventItem(sanitized));
        postInvalidateOnAnimation();
    }

    public void addComment(String content, String mail) {
        if (!overlayActive) {
            return;
        }
        densityAccumulator += densityPercent;
        if (densityAccumulator < 100) {
            return;
        }
        densityAccumulator -= 100;
        if (content == null) {
            return;
        }
        String sanitized = content.replace('\n', ' ').replace('\r', ' ').trim();
        if (sanitized.isEmpty()) {
            return;
        }
        if (sanitized.length() > 160) {
            sanitized = sanitized.substring(0, 160) + "…";
        }

        String commands = mail == null ? "" : mail.toLowerCase(Locale.ROOT);
        Position position = CommentCommands.has(commands, "ue")
                ? Position.TOP
                : CommentCommands.has(commands, "shita")
                ? Position.BOTTOM : Position.SCROLL;
        float sizeMultiplier = CommentCommands.has(commands, "big")
                ? 1.35f
                : CommentCommands.has(commands, "small") ? 0.75f : 1.0f;
        float textSize = dp(34) * scale * sizeMultiplier;
        int color = parseColor(commands);
        int lane = switch (position) {
            case TOP -> nextTopLane++;
            case BOTTOM -> nextBottomLane++;
            case SCROLL -> nextScrollLane++;
        };

        if (items.size() >= MAX_COMMENTS) {
            items.remove(0);
        }
        items.add(new Item(sanitized, color, textSize, position, lane));
        postInvalidateOnAnimation();
    }

    public void clearComments() {
        items.clear();
        nextScrollLane = 0;
        nextTopLane = 0;
        nextBottomLane = 0;
        densityAccumulator = 0;
        invalidate();
    }

    public void setOverlayActive(boolean active) {
        overlayActive = active;
        if (!active) {
            removeCallbacks(hideStatus);
            statusShown = false;
            clearComments();
            setVisibility(INVISIBLE);
        } else {
            setVisibility(VISIBLE);
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        float excluded = (1.0f - verticalCoverage) / 2.0f;
        float topInset = getHeight() * excluded;
        float bottomInset = getHeight() * excluded;
        float usableHeight = Math.max(1, getHeight() - topInset - bottomInset);

        Iterator<Item> iterator = items.iterator();
        boolean animationNeeded = false;
        while (iterator.hasNext()) {
            Item item = iterator.next();
            long age = now - item.createdAt;
            long lifetime = item.position == Position.SCROLL
                    ? scrollDurationMs : FIXED_DURATION_MS;
            if (age > lifetime) {
                iterator.remove();
                continue;
            }

            configureTextPaint(item);
            if (item.measuredWidth == 0f) {
                item.measuredWidth = paint.measureText(item.text);
            }
            float laneHeight = item.textSize * 1.25f;
            int laneCount = Math.max(1, (int) (usableHeight / laneHeight));
            int normalizedLane = Math.floorMod(item.lane, laneCount);
            float y;
            float x;
            if (item.position == Position.SCROLL) {
                float progress = age / (float) scrollDurationMs;
                x = getWidth() - progress * (getWidth() + item.measuredWidth);
                y = topInset + (normalizedLane + 1) * laneHeight;
                animationNeeded = true;
            } else if (item.position == Position.TOP) {
                x = (getWidth() - item.measuredWidth) / 2f;
                y = topInset + (normalizedLane + 1) * laneHeight;
                animationNeeded = true;
            } else {
                x = (getWidth() - item.measuredWidth) / 2f;
                y = getHeight() - bottomInset - normalizedLane * laneHeight;
                animationNeeded = true;
            }
            drawOutlinedText(canvas, item.text, x, y, item.color);
        }

        drawStatusBadge(canvas);
        boolean eventAnimationNeeded = drawEventLog(canvas, now);
        if (animationNeeded || eventAnimationNeeded) {
            postInvalidateOnAnimation();
        }
    }

    private void configureTextPaint(Item item) {
        paint.setTextSize(item.textSize);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawOutlinedText(Canvas canvas, String text, float x, float y, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(dp(1), paint.getTextSize() * outlineRatio));
        paint.setColor(withAlpha(Color.BLACK, commentAlpha));
        canvas.drawText(text, x, y, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(color, commentAlpha));
        canvas.drawText(text, x, y, paint);
    }

    private void drawStatusBadge(Canvas canvas) {
        if (!statusEnabled || !statusShown) {
            return;
        }
        String label = channelLabel.isEmpty() ? status : channelLabel + "  " + status;
        if (label.isEmpty()) {
            return;
        }
        float padding = dp(12);
        float width = badgePaint.measureText(label) + padding * 2;
        float height = dp(38);
        badgePaint.setColor(0xA0121720);
        canvas.drawRoundRect(dp(20), dp(18), dp(20) + width, dp(18) + height,
                dp(8), dp(8), badgePaint);
        badgePaint.setColor(Color.WHITE);
        canvas.drawText(label, dp(20) + padding, dp(18) + dp(25), badgePaint);
    }

    private boolean drawEventLog(Canvas canvas, long now) {
        if (!eventLogEnabled) {
            return false;
        }
        events.removeIf(event -> now - event.createdAt > EVENT_DURATION_MS);
        if (events.isEmpty()) {
            return false;
        }
        badgePaint.setTextSize(dp(13));
        float left = dp(20);
        float top = statusEnabled && statusShown ? dp(64) : dp(18);
        float height = dp(29);
        float padding = dp(9);
        for (EventItem event : events) {
            String label = "• " + event.text;
            float width = badgePaint.measureText(label) + padding * 2;
            badgePaint.setColor(0xB018202A);
            canvas.drawRoundRect(left, top, left + width, top + height,
                    dp(6), dp(6), badgePaint);
            badgePaint.setColor(0xFFD8E7F0);
            canvas.drawText(label, left + padding, top + dp(20), badgePaint);
            top += height + dp(4);
        }
        badgePaint.setTextSize(dp(16));
        return true;
    }

    private int parseColor(String commands) {
        if (CommentCommands.has(commands, "red")) return Color.RED;
        if (CommentCommands.has(commands, "pink")) return 0xFFFF8080;
        if (CommentCommands.has(commands, "orange")) return 0xFFFFCC00;
        if (CommentCommands.has(commands, "yellow")) return Color.YELLOW;
        if (CommentCommands.has(commands, "green")) return 0xFF00CC66;
        if (CommentCommands.has(commands, "cyan")) return Color.CYAN;
        if (CommentCommands.has(commands, "blue")) return 0xFF3399FF;
        if (CommentCommands.has(commands, "purple")) return 0xFFC080FF;
        if (CommentCommands.has(commands, "black")) return 0xFF444444;
        return Color.WHITE;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
