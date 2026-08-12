package com.yukkurimatchatea.nxdanmakutv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class ChannelFollowAccessibilityService extends AccessibilityService {
    private static final int MAX_NODES = 300;
    private static final long DETECTION_DEBOUNCE_MS = 500L;
    private static final int MAX_VISIBILITY_NODES = 100;
    private static final long BLOCKING_STABILITY_MS = 700L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder digitBuffer = new StringBuilder();
    private AppPreferences preferences;
    private String currentChannelId;
    private String tvPackage;
    private String foregroundPackage;
    private Boolean lastPublishedVisibility;
    private boolean blockingUiVisible;
    private long blockingCandidateSince;
    private long lastDetectionAt;

    private final Runnable clearDigits = () -> digitBuffer.setLength(0);
    private final Runnable scanWindow = this::scanActiveWindow;
    private final Runnable evaluateWindows = this::evaluateWindowVisibility;

    @Override
    protected void onServiceConnected() {
        preferences = new AppPreferences(this);
        currentChannelId = preferences.lastChannel();
        tvPackage = preferences.tvPackage();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            handleAccessibilityEvent(event);
        } catch (RuntimeException error) {
            publishDiagnostic("追従処理エラー: " + safeMessage(error));
        }
    }

    private void handleAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        CharSequence packageName = event.getPackageName();
        String eventPackage = packageName == null ? "" : packageName.toString();
        if (!eventPackage.isEmpty()) {
            foregroundPackage = eventPackage;
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            handler.removeCallbacks(evaluateWindows);
            handler.postDelayed(evaluateWindows, 120L);
            handler.postDelayed(evaluateWindows, 500L);
            handler.postDelayed(evaluateWindows, 1100L);
        }

        if (!isFollowing() || getPackageName().equals(eventPackage)) {
            return;
        }

        StringBuilder text = new StringBuilder();
        appendText(text, event.getText());
        if (event.getContentDescription() != null) {
            text.append(' ').append(event.getContentDescription());
        }
        detectAndPublish(text.toString(), "tv-osd-event", eventPackage);

        handler.removeCallbacks(scanWindow);
        handler.postDelayed(scanWindow, DETECTION_DEBOUNCE_MS);
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        try {
            return handleKeyEvent(event);
        } catch (RuntimeException error) {
            publishDiagnostic("リモコン判定エラー: " + safeMessage(error));
            return false;
        }
    }

    private boolean handleKeyEvent(KeyEvent event) {
        if (!isFollowing() || event == null || event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }

        int keyCode = event.getKeyCode();
        int digit = digitForKeyCode(keyCode);
        if (digit >= 0) {
            digitBuffer.append(digit);
            handler.removeCallbacks(clearDigits);
            handler.postDelayed(clearDigits, 1200L);
            try {
                int number = Integer.parseInt(digitBuffer.toString());
                ChannelCatalog.Channel channel = ChannelCatalog.byRemoteNumber(number);
                if (channel != null) {
                    publish(channel, "remote-number");
                }
            } catch (NumberFormatException ignored) {
                digitBuffer.setLength(0);
            }
            scheduleScans();
            return false;
        }

        if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            int direction = keyCode == KeyEvent.KEYCODE_CHANNEL_UP ? 1 : -1;
            ChannelCatalog.Channel adjacent = ChannelCatalog.adjacent(currentChannelId, direction);
            if (adjacent != null) {
                publish(adjacent, "remote-channel-step");
            }
            scheduleScans();
        } else if (keyCode == KeyEvent.KEYCODE_TV_INPUT
                || keyCode == KeyEvent.KEYCODE_GUIDE
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            scheduleScans();
        }

        // Observation only: always allow the TV application to receive the key.
        return false;
    }

    private void scheduleScans() {
        handler.removeCallbacks(scanWindow);
        handler.postDelayed(scanWindow, 350L);
        handler.postDelayed(scanWindow, 900L);
        handler.postDelayed(scanWindow, 1500L);
    }

    private void scanActiveWindow() {
        if (!isFollowing()) {
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }

        StringBuilder visibleText = new StringBuilder();
        CharSequence rootPackageValue = root.getPackageName();
        String rootPackage = rootPackageValue == null
                ? foregroundPackage : rootPackageValue.toString();
        Deque<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
        nodes.add(root);
        int visited = 0;
        try {
            while (!nodes.isEmpty() && visited++ < MAX_NODES) {
                AccessibilityNodeInfo node = nodes.removeFirst();
                if (node.getText() != null) {
                    visibleText.append(' ').append(node.getText());
                }
                if (node.getContentDescription() != null) {
                    visibleText.append(' ').append(node.getContentDescription());
                }
                for (int i = 0; i < node.getChildCount(); i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) {
                        nodes.addLast(child);
                    }
                }
            }
        } finally {
            root.recycle();
        }
        detectAndPublish(visibleText.toString(), "tv-window", rootPackage);
    }

    private void evaluateWindowVisibility() {
        if (tvPackage == null || tvPackage.isEmpty()) {
            return;
        }
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) {
            return;
        }

        float screenArea = getResources().getDisplayMetrics().widthPixels
                * (float) getResources().getDisplayMetrics().heightPixels;
        boolean tvWindowPresent = false;
        boolean blockingWindowPresent = false;
        String blockingReason = "";
        Rect bounds = new Rect();

        for (AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            String packageName = "";
            float visibleContentRatio = 0f;
            if (root != null) {
                CharSequence value = root.getPackageName();
                packageName = value == null ? "" : value.toString();
                visibleContentRatio = visibleContentRatio(root, screenArea);
                root.recycle();
            }

            boolean isTvWindow = tvPackage.equals(packageName);
            if (isTvWindow) {
                tvWindowPresent = true;
            }

            window.getBoundsInScreen(bounds);
            float areaRatio = screenArea <= 0f
                    ? 0f : (bounds.width() * (float) bounds.height()) / screenArea;
            boolean systemWindow = window.getType() == AccessibilityWindowInfo.TYPE_SYSTEM;
            boolean focusedWindow = window.isFocused();

            if (WindowVisibilityPolicy.blocksComments(
                    isTvWindow,
                    getPackageName().equals(packageName),
                    systemWindow,
                    focusedWindow,
                    areaRatio,
                    visibleContentRatio)) {
                blockingWindowPresent = true;
                blockingReason = packageName.isEmpty()
                        ? "不明なウィンドウ"
                        : packageName;
            }
        }

        if (blockingWindowPresent) {
            long now = SystemClock.uptimeMillis();
            if (blockingCandidateSince == 0L) {
                blockingCandidateSince = now;
            }
            if (now - blockingCandidateSince >= BLOCKING_STABILITY_MS) {
                blockingUiVisible = true;
                if (!Boolean.FALSE.equals(lastPublishedVisibility)) {
                    publishDiagnostic("テレビ表示を停止: " + blockingReason);
                }
                publishTvVisibility(false);
            }
        } else if (tvWindowPresent) {
            blockingCandidateSince = 0L;
            blockingUiVisible = false;
            publishTvVisibility(true);
        } else {
            blockingCandidateSince = 0L;
        }
    }

    private void detectAndPublish(String text, String source, String sourcePackage) {
        ChannelCatalog.Channel channel = ChannelCatalog.detect(text);
        if (channel != null) {
            publish(channel, source, sourcePackage);
        }
    }

    private void publish(ChannelCatalog.Channel channel, String source) {
        publish(channel, source, foregroundPackage);
    }

    private void publish(
            ChannelCatalog.Channel channel,
            String source,
            String sourcePackage
    ) {
        boolean trustedTvSource = sourcePackage != null
                && !sourcePackage.isBlank()
                && !getPackageName().equals(sourcePackage)
                && !isSystemUi(sourcePackage);
        if (trustedTvSource) {
            tvPackage = sourcePackage;
            preferences.setTvPackage(sourcePackage);
            blockingUiVisible = false;
        }
        if (trustedTvSource || !blockingUiVisible) {
            publishTvVisibility(true);
        }

        long now = System.currentTimeMillis();
        if (channel.id().equals(currentChannelId) && now - lastDetectionAt < 2500L) {
            return;
        }
        currentChannelId = channel.id();
        lastDetectionAt = now;
        preferences.setLastChannel(channel.id());

        Intent intent = new Intent(FollowContract.ACTION_CHANNEL_DETECTED)
                .setPackage(getPackageName())
                .putExtra(FollowContract.EXTRA_CHANNEL_ID, channel.id())
                .putExtra(FollowContract.EXTRA_CHANNEL_NAME, channel.name())
                .putExtra(FollowContract.EXTRA_SOURCE, source)
                .putExtra(FollowContract.EXTRA_TV_VISIBLE, !blockingUiVisible);
        sendBroadcast(intent);
    }

    private void publishTvVisibility(boolean visible) {
        if (lastPublishedVisibility != null && lastPublishedVisibility == visible) {
            return;
        }
        lastPublishedVisibility = visible;
        sendBroadcast(new Intent(FollowContract.ACTION_TV_VISIBILITY_CHANGED)
                .setPackage(getPackageName())
                .putExtra(FollowContract.EXTRA_VISIBLE, visible));
    }

    private void publishDiagnostic(String message) {
        sendBroadcast(new Intent(FollowContract.ACTION_DIAGNOSTIC_EVENT)
                .setPackage(getPackageName())
                .putExtra(FollowContract.EXTRA_MESSAGE, message));
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }

    private static boolean isSystemUi(String packageName) {
        return "com.android.systemui".equals(packageName)
                || "android".equals(packageName);
    }

    private static float visibleContentRatio(AccessibilityNodeInfo root, float screenArea) {
        if (root == null || screenArea <= 0f) {
            return 0f;
        }
        Deque<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
        nodes.add(root);
        Rect contentBounds = new Rect();
        Rect nodeBounds = new Rect();
        boolean hasContent = false;
        int visited = 0;
        while (!nodes.isEmpty() && visited++ < MAX_VISIBILITY_NODES) {
            AccessibilityNodeInfo node = nodes.removeFirst();
            // Full-screen transparent SystemUI roots are often focusable/clickable.
            // Only semantic content counts toward the visible modal footprint.
            boolean meaningful = node.isVisibleToUser()
                    && (node.getText() != null
                    || node.getContentDescription() != null);
            if (meaningful) {
                node.getBoundsInScreen(nodeBounds);
                if (!nodeBounds.isEmpty()) {
                    if (hasContent) {
                        contentBounds.union(nodeBounds);
                    } else {
                        contentBounds.set(nodeBounds);
                        hasContent = true;
                    }
                }
            }
            for (int index = 0; index < node.getChildCount(); index++) {
                AccessibilityNodeInfo child = node.getChild(index);
                if (child != null) {
                    nodes.addLast(child);
                }
            }
        }
        return hasContent
                ? (contentBounds.width() * (float) contentBounds.height()) / screenArea
                : 0f;
    }

    private boolean isFollowing() {
        if (preferences == null) {
            preferences = new AppPreferences(this);
        }
        return preferences.autoFollow();
    }

    private static int digitForKeyCode(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return keyCode - KeyEvent.KEYCODE_0;
        }
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            return keyCode - KeyEvent.KEYCODE_NUMPAD_0;
        }
        return -1;
    }

    private static void appendText(StringBuilder target, List<CharSequence> values) {
        for (CharSequence value : values) {
            if (value != null) {
                target.append(' ').append(value);
            }
        }
    }
}
