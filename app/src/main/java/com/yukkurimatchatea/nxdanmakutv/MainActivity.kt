package com.yukkurimatchatea.nxdanmakutv

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_NOTIFICATIONS = 10
        private const val CATEGORY_APP_LIVE_TV = "android.intent.category.APP_LIVE_TV"
    }

    private lateinit var preferences: AppPreferences
    private lateinit var updateChecker: GitHubUpdateChecker
    private var uiState by mutableStateOf(UiState())
    private var updateInfo by mutableStateOf<GitHubUpdateChecker.UpdateInfo?>(null)
    private var regionDialogRequired by mutableStateOf(false)
    private var showRegionDialog by mutableStateOf(false)
    private var showIntroDialog by mutableStateOf(false)
    private var updateCheckStarted = false
    private var downloadReceiverRegistered = false

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId == preferences.updateDownloadId()) {
                installDownloadedUpdate(completedId, true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AppPreferences(this)
        updateChecker = GitHubUpdateChecker()
        registerDownloadReceiver()
        refreshUi()
        showIntroDialog = !preferences.hasRegionSelection()
        setContent {
            NxTvTheme {
                SettingsScreen()
                if (showIntroDialog) IntroDialog()
                if (showRegionDialog) RegionPickerDialog(regionDialogRequired)
                updateInfo?.let { UpdateDialog(it) }
            }
        }
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (::preferences.isInitialized) refreshUi()
        val pending = if (::preferences.isInitialized) preferences.updateDownloadId() else -1L
        if (pending >= 0L) installDownloadedUpdate(pending, false)
        if (!updateCheckStarted && ::preferences.isInitialized && preferences.autoUpdateCheck()) {
            updateCheckStarted = true
            checkForUpdates(false)
        }
    }

    override fun onDestroy() {
        if (downloadReceiverRegistered) {
            unregisterReceiver(downloadReceiver)
            downloadReceiverRegistered = false
        }
        if (::updateChecker.isInitialized) updateChecker.shutdown()
        super.onDestroy()
    }

    @Composable
    private fun SettingsScreen() {
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 56.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { HeroPanel() }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("準備" to 2, "動作" to 6, "表示" to 11, "診断" to 20).forEach { (label, index) ->
                            OutlinedButton(
                                onClick = { scope.launch { listState.animateScrollToItem(index) } },
                                modifier = Modifier.weight(1f)
                            ) { Text(label) }
                        }
                    }
                }
                item { SectionHeading("クイックスタート", "必要な準備と基本操作") }
                item { ReadinessPanel() }
                item { RegionPanel() }
                item { PrimaryActions() }
                item { SectionHeading("動作設定", "テレビ画面上での振る舞い") }
                item {
                    SettingToggle("チャンネル自動追従", "地デジの選局に実況先を合わせます", uiState.autoFollow) {
                        preferences.setAutoFollow(it); refreshUi()
                    }
                }
                item {
                    SettingToggle("接続状態を3秒表示", "接続成功後に左上の状態表示を自動で隠します", uiState.showStatus) {
                        preferences.setShowStatusBadge(it); notifySettingsChanged(); refreshUi()
                    }
                }
                item {
                    SettingToggle("左上に動作ログを表示", "接続・復旧・画面判定のイベントを確認できます", uiState.showEventLog) {
                        preferences.setShowEventLog(it); notifySettingsChanged(); refreshUi()
                    }
                }
                item {
                    SettingToggle("GitHubから更新を自動確認", "起動時に新しいリリースを確認します", uiState.autoUpdate) {
                        preferences.setAutoUpdateCheck(it); refreshUi(); if (it) checkForUpdates(false)
                    }
                }
                item { SectionHeading("コメント表示", "見やすさと映像同期を調整") }
                item { Stepper("文字サイズ", "コメント全体の大きさ", 70, 160, 10, uiState.textScale, { "${it}%" }, { preferences.setTextScale(it / 100f) }) }
                item { Stepper("流れる速さ", "画面を横切るまでの時間", 3, 10, 1, uiState.speed, { "${it}秒" }, { preferences.setScrollDurationSeconds(it) }) }
                item { Stepper("透明度", "映像に対するコメントの濃さ", 30, 100, 10, uiState.opacity, { "${it}%" }, { preferences.setOpacityPercent(it) }) }
                item { Stepper("コメント密度", "受信コメントを均等に間引く割合", 25, 100, 25, uiState.density, { "${it}%" }, { preferences.setDensityPercent(it) }) }
                item { Stepper("表示範囲", "上下の余白を除いた使用領域", 50, 100, 10, uiState.coverage, { "${it}%" }, { preferences.setVerticalCoveragePercent(it) }) }
                item { Stepper("縁取り", "文字の黒い縁の太さ", 1, 3, 1, uiState.outline, ::outlineLabel, { preferences.setOutlineLevel(it) }) }
                item { Stepper("コメント遅延", "地デジ映像とのタイミング調整", 0, 30, 1, uiState.delaySeconds, { "${it}秒" }, { preferences.setCommentDelayMs(it * 1000L) }) }
                item {
                    OutlinedButton(onClick = {
                        preferences.resetDisplaySettings(); notifySettingsChanged(); refreshUi()
                    }, modifier = Modifier.fillMaxWidth()) { Text("表示設定を初期値に戻す") }
                }
                item { SectionHeading("チャンネル診断", "選択地域の実況接続を手動テスト") }
                uiState.region?.let { region ->
                    items(region.stations().filter { it.supported() }.chunked(3)) { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { station ->
                                OutlinedButton(
                                    onClick = { manualChannel(station.channelId()) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("${station.remoteNumber()}  ${station.name()}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                item { InfoPanel() }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    @Composable
    private fun HeroPanel() {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier.size(62.dp)
                    )
                }
                Spacer(Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    Text("NX弾幕TV", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("地デジの選局にNX-Jikkyoを完全自動追従", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("v${versionName()}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    Pill(
                        if (uiState.ready) "● 使用できます" else "● 初回設定が必要",
                        if (uiState.ready) SuccessContainer else WarningContainer,
                        if (uiState.ready) Success else Warning
                    )
                }
            }
        }
    }

    @Composable
    private fun ReadinessPanel() {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("準備状態", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusPill(if (uiState.overlayGranted) "✓ オーバーレイ" else "! オーバーレイ未許可", uiState.overlayGranted)
                    StatusPill(
                        if (uiState.followEnabled) "✓ 自動追従" else if (uiState.autoFollow) "! 自動追従未許可" else "自動追従オフ",
                        uiState.followEnabled || !uiState.autoFollow
                    )
                }
                Text(
                    if (uiState.ready) "準備は完了しています。コメント表示を開始できます。"
                    else "未設定の項目を選ぶと、Android TVの許可画面を開きます。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun RegionPanel() {
        Card(onClick = { openRegionPicker(false) }, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("放送地域", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(uiState.region?.name() ?: "都道府県を選択", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        uiState.region?.let { "${it.stations().size}局のリモコン番号へ自動追従" }
                            ?: "数字キーとチャンネル上下の追従に必要です",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("変更  ›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun PrimaryActions() {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = ::openOverlaySettings, modifier = Modifier.weight(1f)) { Text("オーバーレイ権限") }
                OutlinedButton(onClick = ::openAccessibilitySettings, modifier = Modifier.weight(1f)) { Text("自動追従権限") }
                Button(onClick = { startOverlay(true) }, modifier = Modifier.weight(1.45f)) { Text("コメント表示を開始") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = ::stopOverlay, modifier = Modifier.weight(1f)) { Text("コメント停止") }
                OutlinedButton(onClick = ::openLiveTv, modifier = Modifier.weight(1f)) { Text("地デジへ戻る") }
                OutlinedButton(onClick = { checkForUpdates(true) }, modifier = Modifier.weight(1f)) { Text("更新を確認") }
            }
        }
    }

    @Composable
    private fun SettingToggle(title: String, description: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
        Card(onClick = { onChanged(!checked) }, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = checked, onCheckedChange = null)
            }
        }
    }

    @Composable
    private fun Stepper(
        title: String,
        description: String,
        min: Int,
        max: Int,
        step: Int,
        current: Int,
        formatter: (Int) -> String,
        save: (Int) -> Unit
    ) {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = {
                    val value = (current - step).coerceAtLeast(min); save(value); notifySettingsChanged(); refreshUi()
                }, enabled = current > min) { Text("−", fontSize = 22.sp) }
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp).width(112.dp).height(52.dp)
                        .clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) { Text(formatter(current), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = {
                    val value = (current + step).coerceAtMost(max); save(value); notifySettingsChanged(); refreshUi()
                }, enabled = current < max) { Text("＋", fontSize = 22.sp) }
            }
        }
    }

    @Composable
    private fun SectionHeading(title: String, subtitle: String) {
        Column(modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun InfoPanel() {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text("自動追従の判定方式", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "TV Provider → メーカー局表示 → AccessibilityのOSD → リモコンキーの順で判定します。キーは監視のみで、純正テレビアプリへそのまま渡します。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun IntroDialog() {
        AppDialog(title = "放送地域を設定します", dismissible = false, actions = {
            Button(onClick = {
                showIntroDialog = false
                openRegionPicker(true)
            }) { Text("都道府県を選ぶ") }
        }) {
            Text("リモコン番号は地域によって異なります。お住まいの都道府県を選ぶと、数字キーとチャンネル上下へ正しく追従します。あとから設定画面で変更できます。")
        }
    }

    @Composable
    private fun RegionPickerDialog(required: Boolean) {
        val regions = RegionChannelCatalog.all()
        var selectedId by remember(showRegionDialog, uiState.region?.id()) {
            mutableStateOf(uiState.region?.id())
        }
        AppDialog(title = "都道府県を選択", dismissible = !required, onDismiss = { showRegionDialog = false }, actions = {
            if (!required) OutlinedButton(onClick = { showRegionDialog = false }) { Text("キャンセル") }
            Button(onClick = {
                val id = selectedId
                if (id == null) {
                    Toast.makeText(this@MainActivity, "都道府県を1つ選んでください", Toast.LENGTH_SHORT).show()
                } else {
                    preferences.setRegionId(id)
                    showRegionDialog = false
                    regionDialogRequired = false
                    refreshUi()
                    notifySettingsChanged()
                }
            }) { Text("保存") }
        }) {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(430.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(regions) { region ->
                    Card(onClick = { selectedId = region.id() }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedId == region.id(), onClick = null)
                            Spacer(Modifier.width(14.dp))
                            Text(region.name(), fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun UpdateDialog(update: GitHubUpdateChecker.UpdateInfo) {
        AppDialog(title = "NX弾幕TVの更新があります", onDismiss = { updateInfo = null }, actions = {
            OutlinedButton(onClick = { updateInfo = null }) { Text("あとで") }
            OutlinedButton(onClick = {
                startActivity(Intent(Intent.ACTION_VIEW, update.releaseUrl().toUri()))
            }) { Text("GitHubを見る") }
            Button(onClick = { updateInfo = null; downloadUpdate(update) }) { Text("アップデート") }
        }) {
            Text("現在: v${versionName()}\n最新版: v${update.version()}", fontWeight = FontWeight.Bold)
            if (update.notes().isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(update.notes().take(700), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun AppDialog(
        title: String,
        dismissible: Boolean = true,
        onDismiss: () -> Unit = {},
        actions: @Composable RowScope.() -> Unit,
        content: @Composable ColumnScope.() -> Unit
    ) {
        Dialog(onDismissRequest = { if (dismissible) onDismiss() }) {
            Box(modifier = Modifier.fillMaxWidth().padding(36.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(28.dp)) {
                    Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(18.dp))
                    content()
                    Spacer(Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        content = actions
                    )
                }
            }
        }
    }

    @Composable
    private fun Pill(text: String, background: Color, foreground: Color) {
        Text(
            text,
            modifier = Modifier.clip(CircleShape).background(background).padding(horizontal = 14.dp, vertical = 7.dp),
            color = foreground,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }

    @Composable
    private fun StatusPill(text: String, ready: Boolean) {
        Pill(text, if (ready) SuccessContainer else ErrorContainer, if (ready) Success else Error)
    }

    private fun openRegionPicker(required: Boolean) {
        regionDialogRequired = required
        showRegionDialog = true
    }

    private fun refreshUi() {
        val overlay = Settings.canDrawOverlays(this)
        val follow = isFollowServiceEnabled()
        val followReady = follow || !preferences.autoFollow()
        uiState = UiState(
            overlayGranted = overlay,
            followEnabled = follow,
            ready = overlay && followReady && preferences.hasRegionSelection(),
            autoFollow = preferences.autoFollow(),
            showStatus = preferences.showStatusBadge(),
            showEventLog = preferences.showEventLog(),
            autoUpdate = preferences.autoUpdateCheck(),
            region = RegionChannelCatalog.byId(preferences.regionId()),
            textScale = (preferences.textScale() * 100).toInt(),
            speed = preferences.scrollDurationSeconds(),
            opacity = preferences.opacityPercent(),
            density = preferences.densityPercent(),
            coverage = preferences.verticalCoveragePercent(),
            outline = preferences.outlineLevel(),
            delaySeconds = (preferences.commentDelayMs() / 1000L).toInt()
        )
    }

    private fun startOverlay(returnToTv: Boolean) {
        if (!preferences.hasRegionSelection()) { openRegionPicker(true); return }
        if (!Settings.canDrawOverlays(this)) { openOverlaySettings(); return }
        if (preferences.autoFollow() && !isFollowServiceEnabled()) { openAccessibilitySettings(); return }
        startForegroundService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START))
        if (returnToTv) openLiveTv()
    }

    private fun stopOverlay() {
        startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
    }

    private fun manualChannel(channelId: String) {
        if (!Settings.canDrawOverlays(this)) { openOverlaySettings(); return }
        startForegroundService(Intent(this, OverlayService::class.java)
            .setAction(OverlayService.ACTION_MANUAL_CHANNEL)
            .putExtra(FollowContract.EXTRA_CHANNEL_ID, channelId))
        openLiveTv()
    }

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    private fun openAccessibilitySettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun openLiveTv() {
        try {
            startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, CATEGORY_APP_LIVE_TV))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun checkForUpdates(interactive: Boolean) {
        if (interactive) Toast.makeText(this, "GitHub Releasesを確認中", Toast.LENGTH_SHORT).show()
        val current = versionName()
        updateChecker.check(current, object : GitHubUpdateChecker.Listener {
            override fun onUpdateAvailable(update: GitHubUpdateChecker.UpdateInfo) {
                if (!isFinishing) updateInfo = update
            }
            override fun onUpToDate() {
                if (interactive) Toast.makeText(this@MainActivity, "最新版です（v$current）", Toast.LENGTH_SHORT).show()
            }
            override fun onError(message: String) {
                if (interactive) Toast.makeText(this@MainActivity, "更新確認失敗: $message", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun downloadUpdate(update: GitHubUpdateChecker.UpdateInfo) {
        try {
            val manager = getSystemService(DownloadManager::class.java)
                ?: error("DownloadManagerが利用できません")
            val request = DownloadManager.Request(update.apkUrl().toUri())
                .setTitle("NX弾幕TV v${update.version()}")
                .setDescription("アップデートAPKをダウンロードしています")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "NXDanmakuTV-update-${update.version()}.apk")
            preferences.setUpdateDownloadId(manager.enqueue(request))
            Toast.makeText(this, "更新をダウンロードします", Toast.LENGTH_SHORT).show()
        } catch (error: RuntimeException) {
            Toast.makeText(this, "ダウンロード開始失敗: ${safeMessage(error)}", Toast.LENGTH_LONG).show()
        }
    }

    private fun installDownloadedUpdate(downloadId: Long, notifyFailure: Boolean) {
        val manager = getSystemService(DownloadManager::class.java) ?: return
        try {
            manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                if (!cursor.moveToFirst()) { preferences.setUpdateDownloadId(-1L); return }
                when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_FAILED -> {
                        preferences.setUpdateDownloadId(-1L)
                        if (notifyFailure) Toast.makeText(this, "更新APKのダウンロードに失敗しました", Toast.LENGTH_LONG).show()
                        return
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> Unit
                    else -> return
                }
                val apkUri = manager.getUriForDownloadedFile(downloadId)
                if (apkUri == null || !isValidUpdatePackage(cursor)) {
                    preferences.setUpdateDownloadId(-1L)
                    Toast.makeText(this, "更新APKの検証に失敗しました", Toast.LENGTH_LONG).show()
                    return
                }
                if (!packageManager.canRequestPackageInstalls()) {
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri()))
                    Toast.makeText(this, "NX弾幕TVからのインストールを許可して戻ってください", Toast.LENGTH_LONG).show()
                    return
                }
                preferences.setUpdateDownloadId(-1L)
                startActivity(Intent(Intent.ACTION_VIEW)
                    .setDataAndType(apkUri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (error: RuntimeException) {
            if (notifyFailure) Toast.makeText(this, "更新処理エラー: ${safeMessage(error)}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isValidUpdatePackage(cursor: android.database.Cursor): Boolean {
        val index = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        if (index < 0) return false
        val localUri = cursor.getString(index) ?: return false
        if (!localUri.startsWith("file:")) return false
        val path = localUri.toUri().path ?: return false
        val archive = packageManager.getPackageArchiveInfo(path, 0) ?: return false
        return packageName == archive.packageName && packageVersionCode(archive) > versionCode()
    }

    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        downloadReceiverRegistered = true
    }

    private fun isFollowServiceEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val expected = ComponentName(this, ChannelFollowAccessibilityService::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { service ->
            service.resolveInfo?.serviceInfo?.let {
                expected.packageName == it.packageName && expected.className == it.name
            } == true
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun notifySettingsChanged() {
        sendBroadcast(Intent(FollowContract.ACTION_SETTINGS_CHANGED).setPackage(packageName))
    }

    private fun versionName(): String = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
    private fun versionCode(): Long = try { packageVersionCode(packageManager.getPackageInfo(packageName, 0)) } catch (_: Exception) { 0L }
    @Suppress("DEPRECATION")
    private fun packageVersionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    private fun safeMessage(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    private fun outlineLabel(level: Int): String = when (level) { 1 -> "細い"; 3 -> "太い"; else -> "標準" }
}

private data class UiState(
    val overlayGranted: Boolean = false,
    val followEnabled: Boolean = false,
    val ready: Boolean = false,
    val autoFollow: Boolean = true,
    val showStatus: Boolean = true,
    val showEventLog: Boolean = true,
    val autoUpdate: Boolean = true,
    val region: RegionChannelCatalog.Region? = null,
    val textScale: Int = 100,
    val speed: Int = 6,
    val opacity: Int = 90,
    val density: Int = 100,
    val coverage: Int = 90,
    val outline: Int = 2,
    val delaySeconds: Int = 0
)

private val Background = Color(0xFF0B1016)
private val SurfaceColor = Color(0xFF161D25)
private val SurfaceVariant = Color(0xFF222C37)
private val Primary = Color(0xFF8DDCF6)
private val OnPrimary = Color(0xFF003544)
private val Success = Color(0xFF9BE7B0)
private val SuccessContainer = Color(0xFF173A28)
private val Warning = Color(0xFFFFD28C)
private val WarningContainer = Color(0xFF3B3020)
private val Error = Color(0xFFFFB4AB)
private val ErrorContainer = Color(0xFF482522)

@Composable
private fun NxTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Primary,
            onPrimary = OnPrimary,
            background = Background,
            onBackground = Color(0xFFE8EEF3),
            surface = SurfaceColor,
            onSurface = Color(0xFFE8EEF3),
            surfaceVariant = SurfaceVariant,
            onSurfaceVariant = Color(0xFFB6C4D1),
            error = Error,
            onError = Color(0xFF690005)
        ),
        content = content
    )
}
