package com.yukkurimatchatea.nxdanmakutv;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;

public final class OverlayService extends Service implements NxJikkyoClient.Listener {
    public static final String ACTION_START =
            "com.yukkurimatchatea.nxdanmakutv.action.START";
    public static final String ACTION_STOP =
            "com.yukkurimatchatea.nxdanmakutv.action.STOP";
    public static final String ACTION_MANUAL_CHANNEL =
            "com.yukkurimatchatea.nxdanmakutv.action.MANUAL_CHANNEL";

    private static final int NOTIFICATION_ID = 4106;
    private static final String NOTIFICATION_CHANNEL_ID = "nx_danmaku_overlay";

    private WindowManager windowManager;
    private DanmakuView danmakuView;
    private NxJikkyoClient client;
    private AppPreferences preferences;
    private String activeChannelId;
    private boolean tvVisible;
    private boolean receiverRegistered;

    private final BroadcastReceiver channelReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                handleBroadcast(intent);
            } catch (RuntimeException error) {
                DanmakuView view = danmakuView;
                if (view != null) {
                    view.addEvent("イベント処理エラー: " + safeMessage(error));
                }
            }
        }

        private void handleBroadcast(Intent intent) {
            if (FollowContract.ACTION_DIAGNOSTIC_EVENT.equals(intent.getAction())) {
                DanmakuView view = danmakuView;
                String message = intent.getStringExtra(FollowContract.EXTRA_MESSAGE);
                if (view != null) {
                    view.addEvent(message);
                }
                return;
            }
            if (FollowContract.ACTION_SETTINGS_CHANGED.equals(intent.getAction())) {
                if (danmakuView != null) {
                    danmakuView.setStatusVisible(preferences.showStatusBadge());
                    danmakuView.setEventLogVisible(preferences.showEventLog());
                    danmakuView.applyDisplaySettings(preferences);
                }
                return;
            }
            if (FollowContract.ACTION_TV_VISIBILITY_CHANGED.equals(intent.getAction())) {
                setTvVisible(intent.getBooleanExtra(FollowContract.EXTRA_VISIBLE, false));
                return;
            }
            if (!FollowContract.ACTION_CHANNEL_DETECTED.equals(intent.getAction())) {
                return;
            }
            if (!preferences.autoFollow()) {
                return;
            }
            String id = intent.getStringExtra(FollowContract.EXTRA_CHANNEL_ID);
            String stationName = intent.getStringExtra(FollowContract.EXTRA_CHANNEL_NAME);
            if (intent.getBooleanExtra(FollowContract.EXTRA_TV_VISIBLE, false)) {
                setTvVisible(true);
            }
            if (id == null || id.isBlank()) {
                suspendUnsupportedStation(stationName);
            } else {
                switchChannel(id, "自動追従");
            }
        }
    };

    @SuppressLint("InlinedApi")
    @Override
    public void onCreate() {
        super.onCreate();
        preferences = new AppPreferences(this);
        client = new NxJikkyoClient(this);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("待機中"));

        IntentFilter filter = new IntentFilter(FollowContract.ACTION_CHANNEL_DETECTED);
        filter.addAction(FollowContract.ACTION_SETTINGS_CHANGED);
        filter.addAction(FollowContract.ACTION_TV_VISIBILITY_CHANGED);
        filter.addAction(FollowContract.ACTION_DIAGNOSTIC_EVENT);
        registerReceiver(channelReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ensureOverlay()) {
            return START_NOT_STICKY;
        }

        if (ACTION_MANUAL_CHANNEL.equals(action)) {
            String id = intent.getStringExtra(FollowContract.EXTRA_CHANNEL_ID);
            setTvVisible(true);
            switchChannel(id, "手動テスト");
        } else if (preferences.autoFollow()) {
            activeChannelId = null;
            tvVisible = false;
            danmakuView.setChannel("");
            danmakuView.setStatus("チャンネル判定中");
            danmakuView.setOverlayActive(false);
            client.stop();
        } else {
            setTvVisible(true);
            switchChannel(preferences.lastChannel(), "固定");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(channelReceiver);
            receiverRegistered = false;
        }
        if (client != null) {
            client.shutdown();
        }
        if (danmakuView != null && windowManager != null) {
            try {
                windowManager.removeView(danmakuView);
            } catch (RuntimeException ignored) {
                // The system may already have detached the overlay.
            }
            danmakuView = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onStatus(String status) {
        DanmakuView view = danmakuView;
        if (view != null) {
            view.post(() -> {
                view.setStatus(status);
                view.addEvent(status);
            });
        }
        updateNotification(status);
    }

    @Override
    public void onConnected(String channelId) {
        DanmakuView view = danmakuView;
        if (view != null) {
            view.post(() -> {
                view.setConnectedStatus("コメント受信中 " + channelId);
                view.addEvent("接続完了 " + channelId);
            });
        }
        updateNotification("コメント受信中 " + channelId);
    }

    @Override
    public void onComment(String channelId, String content, String mail, String userId) {
        DanmakuView view = danmakuView;
        if (view == null) {
            return;
        }
        long delay = preferences.commentDelayMs();
        view.postDelayed(() -> {
            try {
                if (view == danmakuView
                        && tvVisible
                        && channelId != null
                        && channelId.equals(activeChannelId)) {
                    view.addComment(content, mail);
                }
            } catch (RuntimeException error) {
                view.addEvent("表示エラー: " + safeMessage(error));
            }
        }, Math.max(0L, delay));
    }

    private boolean ensureOverlay() {
        if (danmakuView != null) {
            return true;
        }
        windowManager = getSystemService(WindowManager.class);
        danmakuView = new DanmakuView(this);
        danmakuView.applyDisplaySettings(preferences);
        danmakuView.setStatusVisible(preferences.showStatusBadge());
        danmakuView.setEventLogVisible(preferences.showEventLog());
        danmakuView.setOverlayActive(false);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            windowManager.addView(danmakuView, params);
        } catch (RuntimeException error) {
            updateNotification("オーバーレイ作成エラー: " + safeMessage(error));
            danmakuView = null;
            stopSelf();
            return false;
        }
        return true;
    }

    private void switchChannel(String channelId, String source) {
        ChannelCatalog.Channel channel = ChannelCatalog.byId(channelId);
        if (channel == null || channel.id().equals(activeChannelId)) {
            return;
        }
        activeChannelId = channel.id();
        preferences.setLastChannel(channel.id());
        danmakuView.setChannel(channel.name());
        danmakuView.setStatus(source + " / 接続中");
        danmakuView.addEvent("チャンネル変更 " + channel.name() + " (" + channel.id() + ")");
        client.connect(channel.id());
    }

    private void suspendUnsupportedStation(String stationName) {
        String label = stationName == null || stationName.isBlank() ? "この放送局" : stationName;
        activeChannelId = "";
        client.stop();
        danmakuView.setChannel(label);
        danmakuView.setStatus("実況チャンネルを特定できません");
        danmakuView.addEvent(label + " は自動対応先なし");
        updateNotification(label + " / 自動対応先なし");
    }

    private void setTvVisible(boolean visible) {
        if (danmakuView == null) {
            return;
        }
        boolean changed = tvVisible != visible;
        tvVisible = visible;
        danmakuView.setOverlayActive(visible);
        if (visible && changed) {
            danmakuView.addEvent("テレビ表示へ復帰");
        }
        if (visible && activeChannelId == null) {
            String fallbackChannel = preferences.lastChannel();
            danmakuView.addEvent("前回チャンネルで接続を復旧 " + fallbackChannel);
            switchChannel(fallbackChannel, "復帰");
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("テレビ上に実況コメントを表示している間に使用します");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification createNotification(String status) {
        Intent activityIntent = new Intent(this, MainActivity.class);
        PendingIntent activity = PendingIntent.getActivity(
                this, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(status)
                .setContentIntent(activity)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        null, "停止", stop).build())
                .build();
    }

    private void updateNotification(String status) {
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, createNotification(status));
            }
        } catch (RuntimeException ignored) {
            // Notification permission or system service failures must not stop comments.
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }
}
