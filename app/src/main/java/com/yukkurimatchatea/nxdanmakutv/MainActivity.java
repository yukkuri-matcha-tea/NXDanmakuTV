package com.yukkurimatchatea.nxdanmakutv;

import android.Manifest;
import android.annotation.SuppressLint;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 10;
    private static final String CATEGORY_APP_LIVE_TV = "android.intent.category.APP_LIVE_TV";

    private AppPreferences preferences;
    private TextView permissionStatus;
    private TextView followStatus;
    private TextView overallStatus;
    private Switch autoFollowSwitch;
    private GitHubUpdateChecker updateChecker;
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
        setContentView(buildContent());
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

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.background));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(52), dp(34), dp(52), dp(40));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout hero = card(0xFF151D27, 28, 26);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setElevation(dp(3));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        logo.setBackground(roundedBackground(0xFF0E141C, 22));
        logo.setPadding(dp(10), dp(10), dp(10), dp(10));
        hero.addView(logo, new LinearLayout.LayoutParams(dp(78), dp(78)));

        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("NX弾幕TV", 32, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.01f);
        heroText.addView(title);
        heroText.addView(text("地デジの選局にNX-Jikkyoを完全自動追従", 16,
                getColor(R.color.text_secondary)), marginTop(4));
        LinearLayout.LayoutParams heroTextParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        heroTextParams.leftMargin = dp(20);
        hero.addView(heroText, heroTextParams);

        LinearLayout heroMeta = new LinearLayout(this);
        heroMeta.setOrientation(LinearLayout.VERTICAL);
        heroMeta.setGravity(Gravity.END);
        heroMeta.addView(chip("v" + versionName(),
                0xFF263341, Color.WHITE));
        overallStatus = chip("準備状態を確認中", 0xFF3A3021, 0xFFFFD28C);
        heroMeta.addView(overallStatus, marginTop(8));
        hero.addView(heroMeta);
        root.addView(hero);
        root.addView(section("クイックスタート"), marginTop(26));

        autoFollowSwitch = new Switch(this);
        autoFollowSwitch.setText("チャンネル自動追従");
        autoFollowSwitch.setTextSize(21);
        autoFollowSwitch.setTextColor(Color.WHITE);
        autoFollowSwitch.setChecked(preferences.autoFollow());
        autoFollowSwitch.setPadding(dp(18), dp(14), dp(18), dp(14));
        autoFollowSwitch.setOnCheckedChangeListener((button, checked) -> {
            preferences.setAutoFollow(checked);
            refreshStatus();
        });
        LinearLayout controlCard = card();
        controlCard.addView(overline("動作"));
        controlCard.addView(autoFollowSwitch);

        Switch statusSwitch = new Switch(this);
        statusSwitch.setText("接続状態を3秒表示");
        statusSwitch.setTextSize(21);
        statusSwitch.setTextColor(Color.WHITE);
        statusSwitch.setChecked(preferences.showStatusBadge());
        statusSwitch.setPadding(dp(18), dp(14), dp(18), dp(14));
        statusSwitch.setOnCheckedChangeListener((button, checked) -> {
            preferences.setShowStatusBadge(checked);
            sendBroadcast(new Intent(FollowContract.ACTION_SETTINGS_CHANGED)
                    .setPackage(getPackageName()));
        });
        controlCard.addView(statusSwitch, marginTop(4));

        Switch eventLogSwitch = new Switch(this);
        eventLogSwitch.setText("左上に動作ログを表示");
        eventLogSwitch.setTextSize(21);
        eventLogSwitch.setTextColor(Color.WHITE);
        eventLogSwitch.setChecked(preferences.showEventLog());
        eventLogSwitch.setPadding(dp(18), dp(14), dp(18), dp(14));
        eventLogSwitch.setOnCheckedChangeListener((button, checked) -> {
            preferences.setShowEventLog(checked);
            notifySettingsChanged();
        });
        controlCard.addView(eventLogSwitch, marginTop(4));

        Switch updateSwitch = new Switch(this);
        updateSwitch.setText(R.string.auto_update_check);
        updateSwitch.setTextSize(21);
        updateSwitch.setTextColor(Color.WHITE);
        updateSwitch.setChecked(preferences.autoUpdateCheck());
        updateSwitch.setPadding(dp(18), dp(14), dp(18), dp(14));
        updateSwitch.setOnCheckedChangeListener((button, checked) -> {
            preferences.setAutoUpdateCheck(checked);
            if (checked) {
                checkForUpdates(false);
            }
        });
        controlCard.addView(updateSwitch, marginTop(4));

        LinearLayout permissionChips = new LinearLayout(this);
        permissionChips.setOrientation(LinearLayout.HORIZONTAL);
        permissionStatus = chip("", 0xFF2A3038, Color.WHITE);
        followStatus = chip("", 0xFF2A3038, Color.WHITE);
        permissionChips.addView(permissionStatus);
        permissionChips.addView(followStatus, leftMargin(8));
        controlCard.addView(permissionChips, marginTop(14));
        root.addView(controlCard, marginTop(12));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.START);
        actions.addView(button("オーバーレイ権限", view -> openOverlaySettings()),
                weightedButton());
        actions.addView(button("自動追従権限", view -> openAccessibilitySettings()),
                weightedButtonWithLeftMargin());
        Button start = button("地デジでコメント表示を開始", view -> startOverlay(true));
        makePrimary(start);
        LinearLayout.LayoutParams startParams = weightedButtonWithLeftMargin();
        startParams.weight = 1.35f;
        actions.addView(start, startParams);
        root.addView(actions, marginTop(12));

        LinearLayout secondary = new LinearLayout(this);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        secondary.addView(button("コメント停止", view -> stopOverlay()), weightedButton());
        secondary.addView(button("地デジへ戻る", view -> openLiveTv()),
                weightedButtonWithLeftMargin());
        secondary.addView(button("更新を確認", view -> checkForUpdates(true)),
                weightedButtonWithLeftMargin());
        root.addView(secondary, marginTop(10));

        root.addView(section("コメント表示"), marginTop(30));
        root.addView(text(
                "リモコンの－／＋で変更すると、表示中の弾幕へすぐ反映されます。",
                15, getColor(R.color.text_secondary)), marginTop(6));

        LinearLayout displaySettings = new LinearLayout(this);
        displaySettings.setOrientation(LinearLayout.VERTICAL);
        displaySettings.addView(settingStepper(
                "文字サイズ", "コメント全体の大きさ",
                70, 160, 10, Math.round(preferences.textScale() * 100),
                value -> value + "%",
                value -> {
                    preferences.setTextScale(value / 100f);
                    notifySettingsChanged();
                }));
        displaySettings.addView(settingStepper(
                "流れる速さ", "画面を横切るまでの時間",
                3, 10, 1, preferences.scrollDurationSeconds(),
                value -> value + "秒",
                value -> {
                    preferences.setScrollDurationSeconds(value);
                    notifySettingsChanged();
                }), marginTop(8));
        displaySettings.addView(settingStepper(
                "透明度", "映像に対するコメントの濃さ",
                30, 100, 10, preferences.opacityPercent(),
                value -> value + "%",
                value -> {
                    preferences.setOpacityPercent(value);
                    notifySettingsChanged();
                }), marginTop(8));
        displaySettings.addView(settingStepper(
                "コメント密度", "受信コメントを均等に間引く割合",
                25, 100, 25, preferences.densityPercent(),
                value -> value + "%",
                value -> {
                    preferences.setDensityPercent(value);
                    notifySettingsChanged();
                }), marginTop(8));
        displaySettings.addView(settingStepper(
                "表示範囲", "上下の余白を除いた使用領域",
                50, 100, 10, preferences.verticalCoveragePercent(),
                value -> value + "%",
                value -> {
                    preferences.setVerticalCoveragePercent(value);
                    notifySettingsChanged();
                }), marginTop(8));
        displaySettings.addView(settingStepper(
                "縁取り", "文字の黒い縁の太さ",
                1, 3, 1, preferences.outlineLevel(),
                MainActivity::outlineLabel,
                value -> {
                    preferences.setOutlineLevel(value);
                    notifySettingsChanged();
                }), marginTop(8));
        displaySettings.addView(settingStepper(
                "コメント遅延", "地デジ映像とのタイミング調整",
                0, 30, 1, (int) (preferences.commentDelayMs() / 1000L),
                value -> value + "秒",
                value -> {
                    preferences.setCommentDelayMs(value * 1000L);
                    notifySettingsChanged();
                }), marginTop(8));
        root.addView(displaySettings, marginTop(12));

        Button reset = button("表示設定を初期値に戻す", view -> {
            preferences.resetDisplaySettings();
            notifySettingsChanged();
            recreate();
        });
        root.addView(reset, marginTop(12));

        root.addView(section("チャンネル診断"), marginTop(30));
        root.addView(text(
                "実機の局検出を確認するためのテストです。通常はテレビ側の選局だけで追従します。",
                15, getColor(R.color.text_secondary)), marginTop(6));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setUseDefaultMargins(true);
        for (ChannelCatalog.Channel channel : ChannelCatalog.all()) {
            Button channelButton = button(
                    channel.remoteNumber() + "  " + channel.name(),
                    view -> manualChannel(channel.id()));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setGravity(Gravity.FILL_HORIZONTAL);
            grid.addView(channelButton, params);
        }
        LinearLayout diagnosticCard = card();
        diagnosticCard.addView(overline("手動判定テスト"));
        diagnosticCard.addView(grid, marginTop(8));
        root.addView(diagnosticCard, marginTop(12));

        LinearLayout infoCard = card(0xFF17232A, 20, 20);
        infoCard.addView(overline("自動追従の判定方式"));
        infoCard.addView(text(
                "TV Provider → メーカー局表示 → AccessibilityのOSD → リモコンキーの順で判定します。"
                        + " キーは監視のみで、純正テレビアプリへそのまま渡します。",
                15, getColor(R.color.text_secondary)), marginTop(8));
        root.addView(infoCard, marginTop(12));
        return scroll;
    }

    private void startOverlay(boolean returnToTv) {
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

    private void stopOverlay() {
        startService(new Intent(this, OverlayService.class)
                .setAction(OverlayService.ACTION_STOP));
    }

    private void manualChannel(String channelId) {
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

    private void openOverlaySettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void openLiveTv() {
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

    private void checkForUpdates(boolean interactive) {
        if (interactive) {
            Toast.makeText(this, "GitHub Releasesを確認中", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(MainActivity.this,
                            "最新版です（v" + currentVersion + "）",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                if (interactive) {
                    Toast.makeText(MainActivity.this,
                            "更新確認失敗: " + message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void showUpdateDialog(GitHubUpdateChecker.UpdateInfo update) {
        String notes = update.notes().replace('\r', ' ').trim();
        if (notes.length() > 700) {
            notes = notes.substring(0, 700) + "…";
        }
        String message = "現在: v" + versionName()
                + "\n最新版: v" + update.version()
                + (notes.isEmpty() ? "" : "\n\n" + notes);
        new AlertDialog.Builder(this)
                .setTitle("NX弾幕TVの更新があります")
                .setMessage(message)
                .setPositiveButton("アップデートする", (dialog, which) ->
                        downloadUpdate(update))
                .setNegativeButton("あとで", null)
                .setNeutralButton("GitHubを見る", (dialog, which) ->
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(update.releaseUrl()))))
                .show();
    }

    private void downloadUpdate(GitHubUpdateChecker.UpdateInfo update) {
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
            Toast.makeText(this, "更新をダウンロードします", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException error) {
            Toast.makeText(this, "ダウンロード開始失敗: " + safeMessage(error),
                    Toast.LENGTH_LONG).show();
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
                    Toast.makeText(this, "更新APKのダウンロードに失敗しました",
                            Toast.LENGTH_LONG).show();
                }
                return;
            }
            if (status != DownloadManager.STATUS_SUCCESSFUL) return;

            Uri apkUri = manager.getUriForDownloadedFile(downloadId);
            if (apkUri == null || !isValidUpdatePackage(cursor)) {
                preferences.setUpdateDownloadId(-1L);
                Toast.makeText(this, "更新APKの検証に失敗しました",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (!getPackageManager().canRequestPackageInstalls()) {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
                Toast.makeText(this,
                        "NX弾幕TVからのインストールを許可して戻ってください",
                        Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, "更新処理エラー: " + safeMessage(error),
                        Toast.LENGTH_LONG).show();
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
        boolean overlay = Settings.canDrawOverlays(this);
        boolean follow = isFollowServiceEnabled();
        permissionStatus.setText(overlay ? "✓ オーバーレイ" : "! オーバーレイ未許可");
        styleStatusChip(permissionStatus, overlay);
        boolean followReady = follow || !preferences.autoFollow();
        followStatus.setText(follow
                ? "✓ 自動追従"
                : preferences.autoFollow() ? "! 自動追従未許可" : "自動追従オフ");
        styleStatusChip(followStatus, followReady);
        boolean ready = overlay && followReady;
        overallStatus.setText(ready ? "● 使用できます" : "● 初回設定が必要");
        overallStatus.setTextColor(ready ? 0xFF9BE7B0 : 0xFFFFD28C);
        overallStatus.setBackground(roundedBackground(
                ready ? 0xFF183B29 : 0xFF3A3021, 50));
        autoFollowSwitch.setChecked(preferences.autoFollow());
    }

    private boolean isFollowServiceEnabled() {
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

    private TextView text(String value, int sp, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        return text;
    }

    private TextView section(String value) {
        TextView text = text(value, 23, Color.WHITE);
        text.setTypeface(null, android.graphics.Typeface.BOLD);
        text.setLetterSpacing(0.02f);
        return text;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setTextColor(Color.WHITE);
        button.setBackground(materialButtonBackground());
        button.setPadding(dp(16), dp(8), dp(16), dp(8));
        button.setOnClickListener(listener);
        button.setFocusable(true);
        button.setOnFocusChangeListener((view, focused) -> view.animate()
                .scaleX(focused ? 1.045f : 1.0f)
                .scaleY(focused ? 1.045f : 1.0f)
                .setDuration(120L)
                .start());
        return button;
    }

    private View settingStepper(
            String title,
            String description,
            int min,
            int max,
            int step,
            int current,
            IntFunction<String> formatter,
            IntConsumer save
    ) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 19, Color.WHITE);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        labels.addView(titleView);
        labels.addView(text(description, 14, getColor(R.color.text_secondary)),
                marginTop(3));
        row.addView(labels, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        int[] value = {Math.max(min, Math.min(max, current))};
        TextView valueView = text(formatter.apply(value[0]), 18, Color.WHITE);
        valueView.setGravity(Gravity.CENTER);
        valueView.setTypeface(null, android.graphics.Typeface.BOLD);
        valueView.setBackground(roundedBackground(0xFF11161D, 14));
        LinearLayout.LayoutParams valueParams =
                new LinearLayout.LayoutParams(dp(116), dp(52));
        valueParams.leftMargin = dp(8);

        Button minus = button("−", ignored -> {
            value[0] = Math.max(min, value[0] - step);
            valueView.setText(formatter.apply(value[0]));
            save.accept(value[0]);
        });
        Button plus = button("＋", ignored -> {
            value[0] = Math.min(max, value[0] + step);
            valueView.setText(formatter.apply(value[0]));
            save.accept(value[0]);
        });
        LinearLayout.LayoutParams keyParams =
                new LinearLayout.LayoutParams(dp(72), dp(52));
        keyParams.leftMargin = dp(8);
        row.addView(minus, keyParams);
        row.addView(valueView, valueParams);
        row.addView(plus, keyParams);
        return row;
    }

    private LinearLayout card() {
        return card(0xFF1B222D, 20, 20);
    }

    private LinearLayout card(int color, int radius, int padding) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        card.setBackground(roundedBackground(color, radius));
        return card;
    }

    private StateListDrawable materialButtonBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                roundedBackground(0xFF315E70, 16));
        states.addState(new int[]{android.R.attr.state_pressed},
                roundedBackground(0xFF244B5B, 16));
        states.addState(new int[]{},
                roundedBackground(0xFF293342, 16));
        return states;
    }

    private GradientDrawable roundedBackground(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private TextView chip(String value, int background, int foreground) {
        TextView chip = text(value, 14, foreground);
        chip.setGravity(Gravity.CENTER);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.setPadding(dp(14), dp(7), dp(14), dp(7));
        chip.setBackground(roundedBackground(background, 50));
        return chip;
    }

    private TextView overline(String value) {
        TextView label = text(value, 13, getColor(R.color.accent));
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setLetterSpacing(0.09f);
        label.setAllCaps(true);
        return label;
    }

    private void styleStatusChip(TextView chip, boolean ready) {
        chip.setTextColor(ready ? 0xFF9BE7B0 : 0xFFFFB4A7);
        chip.setBackground(roundedBackground(
                ready ? 0xFF183B29 : 0xFF462520, 50));
    }

    private void makePrimary(Button button) {
        button.setTextColor(0xFF06202A);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                roundedBackground(0xFFBCEBFA, 18));
        states.addState(new int[]{android.R.attr.state_pressed},
                roundedBackground(0xFF74C8E4, 18));
        states.addState(new int[]{},
                roundedBackground(0xFF8CDDF7, 18));
        button.setBackground(states);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setMinHeight(dp(60));
    }

    private void notifySettingsChanged() {
        sendBroadcast(new Intent(FollowContract.ACTION_SETTINGS_CHANGED)
                .setPackage(getPackageName()));
    }

    private LinearLayout.LayoutParams weightedButton() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams weightedButtonWithLeftMargin() {
        LinearLayout.LayoutParams params = weightedButton();
        params.leftMargin = dp(10);
        return params;
    }

    private LinearLayout.LayoutParams marginTop(int dp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(dp);
        return params;
    }

    private LinearLayout.LayoutParams leftMargin(int value) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(value);
        return params;
    }

    private static String outlineLabel(int level) {
        return switch (level) {
            case 1 -> "細い";
            case 3 -> "太い";
            default -> "標準";
        };
    }

    private String versionName() {
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
