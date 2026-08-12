package com.yukkurimatchatea.nxdanmakutv;

public final class FollowContract {
    public static final String ACTION_CHANNEL_DETECTED =
            "com.yukkurimatchatea.nxdanmakutv.action.CHANNEL_DETECTED";
    public static final String ACTION_SETTINGS_CHANGED =
            "com.yukkurimatchatea.nxdanmakutv.action.SETTINGS_CHANGED";
    public static final String ACTION_TV_VISIBILITY_CHANGED =
            "com.yukkurimatchatea.nxdanmakutv.action.TV_VISIBILITY_CHANGED";
    public static final String ACTION_DIAGNOSTIC_EVENT =
            "com.yukkurimatchatea.nxdanmakutv.action.DIAGNOSTIC_EVENT";
    public static final String EXTRA_CHANNEL_ID = "channel_id";
    public static final String EXTRA_CHANNEL_NAME = "channel_name";
    public static final String EXTRA_REMOTE_NUMBER = "remote_number";
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_VISIBLE = "visible";
    public static final String EXTRA_TV_VISIBLE = "tv_visible";
    public static final String EXTRA_MESSAGE = "message";

    private FollowContract() {}
}
