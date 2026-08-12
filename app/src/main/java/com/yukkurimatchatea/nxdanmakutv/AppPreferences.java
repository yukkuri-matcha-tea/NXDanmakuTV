package com.yukkurimatchatea.nxdanmakutv;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPreferences {
    public static final long DEFAULT_COMMENT_DELAY_MS = 0L;
    public static final float DEFAULT_TEXT_SCALE = 1.0f;
    public static final int DEFAULT_SCROLL_DURATION_SECONDS = 6;
    public static final int DEFAULT_OPACITY_PERCENT = 90;
    public static final int DEFAULT_DENSITY_PERCENT = 100;
    public static final int DEFAULT_VERTICAL_COVERAGE_PERCENT = 90;
    public static final int DEFAULT_OUTLINE_LEVEL = 2;

    private static final String FILE = "nx_danmaku_tv";
    private static final String AUTO_FOLLOW = "auto_follow";
    private static final String LAST_CHANNEL = "last_channel";
    private static final String LAST_REMOTE_NUMBER = "last_remote_number";
    private static final String REGION_ID = "region_id";
    private static final String COMMENT_DELAY_MS = "comment_delay_ms";
    private static final String TEXT_SCALE = "text_scale";
    private static final String SHOW_STATUS_BADGE = "show_status_badge";
    private static final String SHOW_EVENT_LOG = "show_event_log";
    private static final String AUTO_UPDATE_CHECK = "auto_update_check";
    private static final String UPDATE_DOWNLOAD_ID = "update_download_id";
    private static final String TV_PACKAGE = "tv_package";
    private static final String SCROLL_DURATION_SECONDS = "scroll_duration_seconds";
    private static final String OPACITY_PERCENT = "opacity_percent";
    private static final String DENSITY_PERCENT = "density_percent";
    private static final String VERTICAL_COVERAGE_PERCENT = "vertical_coverage_percent";
    private static final String OUTLINE_LEVEL = "outline_level";

    private final SharedPreferences preferences;

    public AppPreferences(Context context) {
        preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean autoFollow() {
        return preferences.getBoolean(AUTO_FOLLOW, true);
    }

    public void setAutoFollow(boolean enabled) {
        preferences.edit().putBoolean(AUTO_FOLLOW, enabled).apply();
    }

    public String lastChannel() {
        return preferences.getString(LAST_CHANNEL, "jk1");
    }

    public void setLastChannel(String channelId) {
        preferences.edit().putString(LAST_CHANNEL, channelId).apply();
    }

    public int lastRemoteNumber() {
        return preferences.getInt(LAST_REMOTE_NUMBER, -1);
    }

    public void setLastRemoteNumber(int remoteNumber) {
        preferences.edit().putInt(LAST_REMOTE_NUMBER, remoteNumber).apply();
    }

    public String regionId() {
        return preferences.getString(REGION_ID, "");
    }

    public boolean hasRegionSelection() {
        return RegionChannelCatalog.byId(regionId()) != null;
    }

    public void setRegionId(String regionId) {
        preferences.edit()
                .putString(REGION_ID, regionId == null ? "" : regionId)
                .putInt(LAST_REMOTE_NUMBER, -1)
                .apply();
    }

    public long commentDelayMs() {
        return preferences.getLong(COMMENT_DELAY_MS, DEFAULT_COMMENT_DELAY_MS);
    }

    public void setCommentDelayMs(long delayMs) {
        preferences.edit().putLong(COMMENT_DELAY_MS, delayMs).apply();
    }

    public float textScale() {
        return preferences.getFloat(TEXT_SCALE, DEFAULT_TEXT_SCALE);
    }

    public void setTextScale(float scale) {
        preferences.edit().putFloat(TEXT_SCALE, scale).apply();
    }

    public boolean showStatusBadge() {
        return preferences.getBoolean(SHOW_STATUS_BADGE, true);
    }

    public void setShowStatusBadge(boolean show) {
        preferences.edit().putBoolean(SHOW_STATUS_BADGE, show).apply();
    }

    public boolean showEventLog() {
        return preferences.getBoolean(SHOW_EVENT_LOG, true);
    }

    public void setShowEventLog(boolean show) {
        preferences.edit().putBoolean(SHOW_EVENT_LOG, show).apply();
    }

    public boolean autoUpdateCheck() {
        return preferences.getBoolean(AUTO_UPDATE_CHECK, true);
    }

    public void setAutoUpdateCheck(boolean enabled) {
        preferences.edit().putBoolean(AUTO_UPDATE_CHECK, enabled).apply();
    }

    public long updateDownloadId() {
        return preferences.getLong(UPDATE_DOWNLOAD_ID, -1L);
    }

    public void setUpdateDownloadId(long downloadId) {
        preferences.edit().putLong(UPDATE_DOWNLOAD_ID, downloadId).apply();
    }

    public String tvPackage() {
        return preferences.getString(TV_PACKAGE, "");
    }

    public void setTvPackage(String packageName) {
        preferences.edit().putString(TV_PACKAGE, packageName == null ? "" : packageName).apply();
    }

    public int scrollDurationSeconds() {
        return preferences.getInt(
                SCROLL_DURATION_SECONDS, DEFAULT_SCROLL_DURATION_SECONDS);
    }

    public void setScrollDurationSeconds(int seconds) {
        preferences.edit().putInt(SCROLL_DURATION_SECONDS, seconds).apply();
    }

    public int opacityPercent() {
        return preferences.getInt(OPACITY_PERCENT, DEFAULT_OPACITY_PERCENT);
    }

    public void setOpacityPercent(int percent) {
        preferences.edit().putInt(OPACITY_PERCENT, percent).apply();
    }

    public int densityPercent() {
        return preferences.getInt(DENSITY_PERCENT, DEFAULT_DENSITY_PERCENT);
    }

    public void setDensityPercent(int percent) {
        preferences.edit().putInt(DENSITY_PERCENT, percent).apply();
    }

    public int verticalCoveragePercent() {
        return preferences.getInt(
                VERTICAL_COVERAGE_PERCENT, DEFAULT_VERTICAL_COVERAGE_PERCENT);
    }

    public void setVerticalCoveragePercent(int percent) {
        preferences.edit().putInt(VERTICAL_COVERAGE_PERCENT, percent).apply();
    }

    public int outlineLevel() {
        return preferences.getInt(OUTLINE_LEVEL, DEFAULT_OUTLINE_LEVEL);
    }

    public void setOutlineLevel(int level) {
        preferences.edit().putInt(OUTLINE_LEVEL, level).apply();
    }

    public void resetDisplaySettings() {
        preferences.edit()
                .putLong(COMMENT_DELAY_MS, DEFAULT_COMMENT_DELAY_MS)
                .putFloat(TEXT_SCALE, DEFAULT_TEXT_SCALE)
                .putInt(SCROLL_DURATION_SECONDS, DEFAULT_SCROLL_DURATION_SECONDS)
                .putInt(OPACITY_PERCENT, DEFAULT_OPACITY_PERCENT)
                .putInt(DENSITY_PERCENT, DEFAULT_DENSITY_PERCENT)
                .putInt(VERTICAL_COVERAGE_PERCENT, DEFAULT_VERTICAL_COVERAGE_PERCENT)
                .putInt(OUTLINE_LEVEL, DEFAULT_OUTLINE_LEVEL)
                .apply();
    }
}
