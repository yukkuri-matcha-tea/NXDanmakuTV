package com.yukkurimatchatea.nxdanmakutv;

final class WindowVisibilityPolicy {
    static final float LARGE_WINDOW_RATIO = 0.22f;
    static final float LARGE_SYSTEM_CONTENT_RATIO = 0.12f;

    private WindowVisibilityPolicy() {
    }

    static boolean blocksComments(
            boolean tvWindow,
            boolean ownWindow,
            boolean systemWindow,
            boolean focusedWindow,
            float windowAreaRatio,
            float visibleContentRatio
    ) {
        if (tvWindow || !focusedWindow) {
            return false;
        }
        if (systemWindow) {
            // Some Android TV devices expose a permanently full-screen, transparent
            // SystemUI window. Its bounds are not evidence that a modal is visible.
            return visibleContentRatio >= LARGE_SYSTEM_CONTENT_RATIO;
        }
        return windowAreaRatio >= LARGE_WINDOW_RATIO;
    }
}
