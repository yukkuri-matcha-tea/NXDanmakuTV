package com.yukkurimatchatea.nxdanmakutv;

import android.Manifest;
import android.annotation.SuppressLint;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

public final class MainActivity extends androidx.activity.ComponentActivity {
    private static final int REQUEST_NOTIFICATIONS = 10;
    private static final String CATEGORY_APP_LIVE_TV = "android.intent.category.APP_LIVE_TV";

    private AppPreferences preferences;
    private GitHubUpdateChecker updateChecker;
    private final SettingsUiState uiState = new SettingsUiState();
    private boolean updateCheckStarted;
    private boolean downloadReceiverRegistered;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long completedId = intent.getLongExtra(
                    DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (completedId == preferences.updateDownloadId()) {
                installDownloadedUpdate(completedId, true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = new AppPreferences(this);
        updateChecker = new GitHubUpdateChecker();
        registerDownloadReceiver();
        MaterialSettings.install(this, uiState);
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        long pendingDownload = preferences.updateDownloadId();
        if (pendingDownload >= 0L) {
            installDownloadedUpdate(pendingDownload, false);
        }
        if (!updateCheckStarted && preferences.autoUpdateCheck()) {
            updateCheckStarted = true;
            checkForUpdates(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (downloadReceiverRegistered) {
            unregisterReceiver(downloadReceiver);
            downloadReceiverRegistered = false;
        }
        if (updateChecker != null) {
            updateChecker.shutdown();
        }
        super.onDestroy();
    }

    void startOverlay(boolean returnToTv) {
        if (!preferences.hasRegionSelection()) {
            showRegionPicker(true);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings();
            return;
        }
        if (preferences.autoFollow() && !isFollowServiceEnabled()) {
            openAccessibilitySettings();
            return;
        }
        Intent service = new Intent(this, OverlayService.class)
                .setAction(OverlayService.ACTION_START);
        startForegroundService(service);
        if (returnToTv) {
            openLiveTv();
        }
    }

    void stopOverlay() {
        startService(new Intent(this, OverlayService.class)
                .setAction(OverlayService.ACTION_STOP));
    }

    void manualChannel(String channelId) {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings();
            return;
        }
        Intent service = new Intent(this, OverlayService.class)
                .setAction(OverlayService.ACTION_MANUAL_CHANNEL)
                .putExtra(FollowContract.EXTRA_CHANNEL_ID, channelId);
        startForegroundService(service);
        openLiveTv();
    }

    void openOverlaySettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    void openLiveTv() {
        Intent intent = Intent.makeMainSelectorActivity(
                Intent.ACTION_MAIN, CATEGORY_APP_LIVE_TV);
        try {
            startActivity(intent);
        } catch (Exception error) {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
        }
    }

    private void showRegionPicker(boolean required) {
        uiState.openRegion(required);
    }

    void checkForUpdates(boolean interactive) {
        if (interactive) {
            uiState.message("GitHub Releasesを確認中");
        }
        String currentVersion = versionName();
        updateChecker.check(currentVersion, new GitHubUpdateChecker.Listener() {
            @Override
            public void onUpdateAvailable(GitHubUpdateChecker.UpdateInfo update) {
                if (!isFinishing()) {
                    showUpdateDialog(update);
                }
            }

            @Override
            public void onUpToDate() {
                if (interactive) {
                    uiState.message("最新版です（v" + currentVersion + "）");
                }
            }

            @Override
            public void onError(String message) {
                if (interactive) {
                    uiState.message("更新確認失敗: " + message);
                }
            }
        });
    }

    private void showUpdateDialog(GitHubUpdateChecker.UpdateInfo update) {
        if (!isFinishing() && !isDestroyed()) uiState.showUpdate(update);
    }

    void downloadUpdate(GitHubUpdateChecker.UpdateInfo update) {
        try {
            DownloadManager manager = getSystemService(DownloadManager.class);
            if (manager == null) {
                throw new IllegalStateException("DownloadManagerが利用できません");
            }
            DownloadManager.Request request = new DownloadManager.Request(
                    Uri.parse(update.apkUrl()))
                    .setTitle("NX弾幕TV v" + update.version())
                    .setDescription("アップデートAPKをダウンロードしています")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS,
                            "NXDanmakuTV-update-" + update.version() + ".apk");
            long downloadId = manager.enqueue(request);
            preferences.setUpdateDownloadId(downloadId);
            uiState.message("更新をダウンロードします");
        } catch (RuntimeException error) {
            uiState.message("ダウンロード開始失敗: " + safeMessage(error));
        }
    }

    private void installDownloadedUpdate(long downloadId, boolean notifyFailure) {
        DownloadManager manager = getSystemService(DownloadManager.class);
        if (manager == null) return;
        try (android.database.Cursor cursor = manager.query(
                new DownloadManager.Query().setFilterById(downloadId))) {
            if (cursor == null || !cursor.moveToFirst()) {
                preferences.setUpdateDownloadId(-1L);
                return;
            }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_FAILED) {
                preferences.setUpdateDownloadId(-1L);
                if (notifyFailure) {
                    uiState.message("更新APKのダウンロードに失敗しました");
                }
                return;
            }
            if (status != DownloadManager.STATUS_SUCCESSFUL) return;

            Uri apkUri = manager.getUriForDownloadedFile(downloadId);
            if (apkUri == null || !isValidUpdatePackage(cursor)) {
                preferences.setUpdateDownloadId(-1L);
                uiState.message("更新APKの検証に失敗しました");
                return;
            }
            if (!getPackageManager().canRequestPackageInstalls()) {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
                uiState.message("NX弾幕TVからのインストールを許可して戻ってください");
                return;
            }
            preferences.setUpdateDownloadId(-1L);
            Intent install = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(apkUri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(install);
        } catch (RuntimeException error) {
            if (notifyFailure) {
                uiState.message("更新処理エラー: " + safeMessage(error));
            }
        }
    }

    private boolean isValidUpdatePackage(android.database.Cursor cursor) {
        int localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
        if (localUriIndex < 0) return false;
        String localUri = cursor.getString(localUriIndex);
        if (localUri == null || !localUri.startsWith("file:")) return false;
        String path = Uri.parse(localUri).getPath();
        if (path == null) return false;
        PackageInfo archive = getPackageManager().getPackageArchiveInfo(path, 0);
        return archive != null
                && getPackageName().equals(archive.packageName)
                && packageVersionCode(archive) > versionCode();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerDownloadReceiver() {
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, filter);
        }
        downloadReceiverRegistered = true;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName() : message;
    }

    private void refreshStatus() {
        uiState.refresh();
    }

    boolean isFollowServiceEnabled() {
        AccessibilityManager manager =
                (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        ComponentName expected =
                new ComponentName(this, ChannelFollowAccessibilityService.class);
        List<AccessibilityServiceInfo> services =
                manager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo service : services) {
            if (service.getResolveInfo() != null
                    && expected.getPackageName().equals(
                    service.getResolveInfo().serviceInfo.packageName)
                    && expected.getClassName().equals(
                    service.getResolveInfo().serviceInfo.name)) {
                return true;
            }
        }
        return false;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }

    void notifySettingsChanged() {
        sendBroadcast(new Intent(FollowContract.ACTION_SETTINGS_CHANGED).setPackage(getPackageName()));
        uiState.refresh();
    }

    String versionName() {
        try {
            return getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "";
        }
    }

    private long versionCode() {
        try {
            return packageVersionCode(getPackageManager()
                    .getPackageInfo(getPackageName(), 0));
        } catch (PackageManager.NameNotFoundException ignored) {
            return 0L;
        }
    }

    @SuppressWarnings("deprecation")
    private static long packageVersionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

}
