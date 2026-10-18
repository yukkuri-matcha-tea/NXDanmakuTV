@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.yukkurimatchatea.nxdanmakutv

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal class SettingsUiState {
    var revision by mutableIntStateOf(0)
        private set
    var regionOpen by mutableStateOf(false)
    var regionRequired by mutableStateOf(false)
    var update by mutableStateOf<GitHubUpdateChecker.UpdateInfo?>(null)
    var notice by mutableStateOf<String?>(null)
    fun refresh() { revision++ }
    fun openRegion(required: Boolean) { regionRequired = required; regionOpen = true }
    fun showUpdate(value: GitHubUpdateChecker.UpdateInfo) { update = value }
    fun message(value: String) { notice = value }
}

internal object MaterialSettings {
    @JvmStatic
    fun install(activity: MainActivity, state: SettingsUiState) {
        activity.enableEdgeToEdge()
        activity.setContent { SettingsApp(activity, state) }
    }
}

private enum class Destination(val title: String, val icon: Int, val filled: Int) {
    Watch("テレビ", R.drawable.ms_live_tv, R.drawable.ms_live_tv_filled),
    Display("表示調整", R.drawable.ms_tune, R.drawable.ms_tune_filled),
    Settings("設定", R.drawable.ms_settings, R.drawable.ms_settings_filled)
}

@Composable
private fun SettingsApp(activity: MainActivity, state: SettingsUiState) {
    val preferences = remember { AppPreferences(activity) }
    val storage = remember { activity.getSharedPreferences("nx_danmaku_tv", 0) }
    val revision = state.revision
    val mode = remember(revision) { storage.getString("ui_theme", "system") ?: "system" }
    val dynamic = remember(revision) { storage.getBoolean("ui_dynamic_color", true) }
    val dark = when (mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val colors = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> runCatching {
            if (dark) dynamicDarkColorScheme(activity) else dynamicLightColorScheme(activity)
        }.getOrElse { if (dark) darkColorScheme() else lightColorScheme() }
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    SideEffect {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), shapes = Shapes()) {
        var page by rememberSaveable { mutableStateOf(Destination.Watch) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        fun changed() { activity.notifySettingsChanged() }
        fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }
        LaunchedEffect(state.notice) {
            state.notice?.let { snackbar.showSnackbar(it); state.notice = null }
        }
        LaunchedEffect(Unit) {
            if (!preferences.hasRegionSelection()) state.openRegion(true)
        }
        BackHandler(page != Destination.Watch && !state.regionOpen && state.update == null) {
            page = Destination.Watch
        }
        Surface(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 720.dp
                Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    if (wide) {
                        NavigationRail(Modifier.fillMaxHeight().padding(horizontal = 8.dp),
                            containerColor = colors.surface,
                            header = { Symbol(R.drawable.ms_live_tv_filled, Modifier.padding(20.dp)) }) {
                            Spacer(Modifier.height(24.dp))
                            Destination.entries.forEach { target ->
                                NavigationRailItem(selected = page == target, onClick = { page = target },
                                    icon = { NavSymbol(target, page == target) },
                                    label = { Text(target.title) }, modifier = Modifier.padding(vertical = 6.dp))
                            }
                        }
                    }
                    Scaffold(
                        modifier = Modifier.weight(1f),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        topBar = {
                            TopAppBar(title = {
                                Column { Text("NX弾幕TV", style = MaterialTheme.typography.titleLarge)
                                    Text(page.title, style = MaterialTheme.typography.labelMedium,
                                        color = colors.onSurfaceVariant) }
                            }, navigationIcon = {
                                if (page != Destination.Watch) IconButton(onClick = { page = Destination.Watch }) {
                                    Icon(painterResource(R.drawable.ms_arrow_back), "テレビ画面へ戻る")
                                }
                            }, actions = { Text("v${activity.versionName()}", Modifier.padding(end = 24.dp),
                                style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant) })
                        },
                        bottomBar = {
                            if (!wide) NavigationBar {
                                Destination.entries.forEach { target ->
                                    NavigationBarItem(selected = page == target, onClick = { page = target },
                                        icon = { NavSymbol(target, page == target) }, label = { Text(target.title) })
                                }
                            }
                        }, snackbarHost = { SnackbarHost(snackbar) }
                    ) { insets ->
                        AnimatedContent(page, modifier = Modifier.padding(insets).fillMaxSize(),
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                            label = "Settings destination") { destination ->
                            val scroll = rememberScrollState()
                            Column(Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally)
                                .widthIn(max = 1040.dp).fillMaxWidth().verticalScroll(scroll)
                                .padding(horizontal = if (wide) 32.dp else 20.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                when (destination) {
                                    Destination.Watch -> {
                                        Text("いつものテレビに、\nみんなのコメントを。",
                                            style = MaterialTheme.typography.headlineMedium)
                                        val overlay = Settings.canDrawOverlays(activity)
                                        val follow = activity.isFollowServiceEnabled()
                                        val ready = overlay && (follow || !preferences.autoFollow())
                                        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(
                                            containerColor = colors.primaryContainer,
                                            contentColor = colors.onPrimaryContainer)) {
                                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    Symbol(if (ready) R.drawable.ms_check_circle else R.drawable.ms_shield)
                                                    Text(if (ready) "コメント表示の準備ができています" else "はじめに必要な権限を設定してください",
                                                        style = MaterialTheme.typography.titleMedium)
                                                }
                                                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(onClick = { activity.startOverlay(true) }, Modifier.heightIn(min = 52.dp)) {
                                                        Symbol(R.drawable.ms_play_arrow); Spacer(Modifier.width(8.dp)); Text("開始してテレビへ")
                                                    }
                                                    OutlinedButton(onClick = { activity.stopOverlay(); message("コメント表示を停止しました") },
                                                        modifier = Modifier.heightIn(min = 52.dp)) {
                                                        Symbol(R.drawable.ms_stop); Spacer(Modifier.width(8.dp)); Text("停止")
                                                    }
                                                }
                                                TextButton(onClick = { activity.startOverlay(false); message("コメント表示の開始を要求しました") }) {
                                                    Text("この画面で開始")
                                                }
                                            }
                                        }
                                        Group("視聴設定") {
                                            ToggleSetting("チャンネルに自動追従", "テレビの選局に合わせて実況を切り替えます",
                                                R.drawable.ms_live_tv, preferences.autoFollow()) { preferences.setAutoFollow(it); changed() }
                                            val region = RegionChannelCatalog.byId(preferences.regionId())
                                            ActionSetting("放送地域", region?.name() ?: "都道府県を選択してください",
                                                R.drawable.ms_location_on) { state.openRegion(false) }
                                        }
                                        Group("アクセス権限") {
                                            ActionSetting("他のアプリの上に表示", if (overlay) "許可済み" else "設定が必要です",
                                                R.drawable.ms_shield, activity::openOverlaySettings)
                                            ActionSetting("チャンネル追従サービス", if (follow) "有効" else "設定が必要です",
                                                R.drawable.ms_live_tv, activity::openAccessibilitySettings)
                                        }
                                        FilledTonalButton(onClick = { page = Destination.Display }) {
                                            Symbol(R.drawable.ms_tune); Spacer(Modifier.width(8.dp)); Text("コメントの見え方を調整")
                                        }
                                    }
                                    Destination.Display -> {
                                        Text("コメントの見え方", style = MaterialTheme.typography.headlineMedium)
                                        Text("文字・速さ・密度を、見やすいバランスに。", color = colors.onSurfaceVariant)
                                        Group("文字と動き") {
                                            ValueSetting("文字サイズ", "標準は100%", (preferences.textScale() * 100).roundToInt(),
                                                60..180, 10, "%") { preferences.setTextScale(it / 100f); changed() }
                                            ValueSetting("流れる時間", "長くするとゆっくり流れます", preferences.scrollDurationSeconds(),
                                                3..10, 1, "秒") { preferences.setScrollDurationSeconds(it); changed() }
                                            ValueSetting("不透明度", "映像に重なるコメントの濃さ", preferences.opacityPercent(),
                                                30..100, 10, "%") { preferences.setOpacityPercent(it); changed() }
                                            ValueSetting("縁取り", "1：細い  2：標準  3：太い", preferences.outlineLevel(),
                                                1..3, 1, "") { preferences.setOutlineLevel(it); changed() }
                                        }
                                        Group("量とタイミング") {
                                            ValueSetting("コメント密度", "受信したコメントを均等に間引きます", preferences.densityPercent(),
                                                25..100, 25, "%") { preferences.setDensityPercent(it); changed() }
                                            ValueSetting("表示範囲", "画面の上下に余白を作ります", preferences.verticalCoveragePercent(),
                                                50..100, 10, "%") { preferences.setVerticalCoveragePercent(it); changed() }
                                            ValueSetting("コメント遅延", "放送映像とのタイミングを調整", (preferences.commentDelayMs() / 1000).toInt(),
                                                0..30, 1, "秒") { preferences.setCommentDelayMs(it * 1000L); changed() }
                                        }
                                        var reset by remember { mutableStateOf(false) }
                                        OutlinedButton(onClick = { reset = true }) {
                                            Symbol(R.drawable.ms_refresh); Spacer(Modifier.width(8.dp)); Text("表示設定を初期値に戻す")
                                        }
                                        if (reset) AlertDialog(onDismissRequest = { reset = false },
                                            icon = { Symbol(R.drawable.ms_refresh) }, title = { Text("表示設定をリセットしますか？") },
                                            text = { Text("文字・速さ・密度・範囲・遅延を初期値に戻します。放送地域や権限は保持されます。") },
                                            confirmButton = { TextButton(onClick = { preferences.resetDisplaySettings(); changed(); reset = false; message("表示設定をリセットしました") }) { Text("リセット") } },
                                            dismissButton = { TextButton(onClick = { reset = false }) { Text("キャンセル") } })
                                    }
                                    Destination.Settings -> {
                                        Text("アプリの設定", style = MaterialTheme.typography.headlineMedium)
                                        Group("外観") {
                                            Text("カラーモード", Modifier.padding(start = 20.dp, top = 16.dp), style = MaterialTheme.typography.titleMedium)
                                            FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                listOf("system" to "端末に合わせる", "light" to "ライト", "dark" to "ダーク").forEach { (key, label) ->
                                                    FilterChip(selected = mode == key, onClick = {
                                                        storage.edit().putString("ui_theme", key).apply(); state.refresh()
                                                    }, label = { Text(label) })
                                                }
                                            }
                                            ToggleSetting("壁紙の色を使う", if (Build.VERSION.SDK_INT >= 31) "端末のテーマカラーを反映します" else "Android 12以降で利用できます",
                                                R.drawable.ms_palette, dynamic, Build.VERSION.SDK_INT >= 31) {
                                                storage.edit().putBoolean("ui_dynamic_color", it).apply(); state.refresh()
                                            }
                                        }
                                        Group("状態表示と更新") {
                                            ToggleSetting("接続状態を3秒表示", "チャンネルの接続成功から3秒で非表示", R.drawable.ms_notifications,
                                                preferences.showStatusBadge()) { preferences.setShowStatusBadge(it); changed() }
                                            ToggleSetting("左上に動作ログを表示", "接続・チャンネル切り替えなどのイベント", R.drawable.ms_history,
                                                preferences.showEventLog()) { preferences.setShowEventLog(it); changed() }
                                            ToggleSetting("起動時にアップデート確認", "GitHubの最新リリースを確認します", R.drawable.ms_system_update,
                                                preferences.autoUpdateCheck()) { preferences.setAutoUpdateCheck(it); changed(); if (it) activity.checkForUpdates(false) }
                                            ActionSetting("アップデートを確認", "インストール済み v${activity.versionName()}",
                                                R.drawable.ms_system_update) { activity.checkForUpdates(true) }
                                            ActionSetting("GitHubを開く", "公開情報・変更点・ソースコード", R.drawable.ms_chevron_right) {
                                                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GitHubUpdateChecker.RELEASES_URL)))
                                            }
                                        }
                                        Group("チャンネル診断") {
                                            Text("選んだ局のコメントを手動で表示します。普段はテレビの選局へ自動追従します。",
                                                Modifier.padding(20.dp), color = colors.onSurfaceVariant)
                                            val region = RegionChannelCatalog.byId(preferences.regionId())
                                            region?.stations()?.filter { it.supported() }?.forEach { station ->
                                                ActionSetting("${station.remoteNumber()}  ${station.name()}", "${station.channelId()} の表示テスト",
                                                    R.drawable.ms_live_tv) { activity.manualChannel(station.channelId()) }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
        if (state.regionOpen) RegionDialog(preferences, state, ::changed)
        state.update?.let { update ->
            // Required first-run region selection always takes precedence.
            if (!state.regionOpen) AlertDialog(onDismissRequest = { state.update = null },
                icon = { Symbol(R.drawable.ms_system_update) }, title = { Text("アップデートがあります") },
                text = { Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("現在 v${activity.versionName()} / 最新 v${update.version()}")
                    Text(update.notes().ifBlank { "変更点はGitHubで確認できます。" })
                    TextButton(onClick = { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl()))) }) { Text("GitHubを見る") }
                } },
                confirmButton = { TextButton(onClick = { state.update = null; activity.downloadUpdate(update) }) { Text("アップデートする") } },
                dismissButton = { TextButton(onClick = { state.update = null }) { Text("あとで") } })
        }
    }
}

@Composable
private fun NavSymbol(destination: Destination, selected: Boolean) {
    Crossfade(selected, animationSpec = tween(180), label = "Symbol fill") { filled ->
        Symbol(if (filled) destination.filled else destination.icon)
    }
}

@Composable
private fun Symbol(resource: Int, modifier: Modifier = Modifier) {
    Icon(painterResource(resource), contentDescription = null, modifier = modifier.size(24.dp))
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) { content() }
    }
}

@Composable
private fun FocusSurface(modifier: Modifier = Modifier, onClick: () -> Unit,
                         content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(if (focused) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh, tween(150), label = "Focus state")
    Surface(onClick = onClick, modifier = modifier.onFocusChanged { focused = it.hasFocus },
        color = background, shape = MaterialTheme.shapes.large,
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        content = content)
}

@Composable
private fun ActionSetting(title: String, summary: String, icon: Int, onClick: () -> Unit) {
    FocusSurface(Modifier.fillMaxWidth().padding(4.dp), onClick) {
        Row(Modifier.padding(16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Symbol(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Symbol(R.drawable.ms_chevron_right)
        }
    }
}

@Composable
private fun ToggleSetting(title: String, summary: String, icon: Int, checked: Boolean,
                          enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val color by animateColorAsState(if (focused) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh, label = "Toggle focus")
    Surface(Modifier.fillMaxWidth().padding(4.dp).onFocusChanged { focused = it.hasFocus }
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        color = color, shape = MaterialTheme.shapes.large,
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
        Row(Modifier.padding(16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f)) {
                Symbol(icon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Switch(checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

@Composable
private fun ValueSetting(title: String, summary: String, value: Int, range: IntRange, step: Int,
                         suffix: String, change: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("$value$suffix", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        }
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(onClick = { change((value - step).coerceIn(range)) }, enabled = value > range.first) {
                Icon(painterResource(R.drawable.ms_remove), "$title を下げる")
            }
            Slider(value.toFloat(), onValueChange = {
                change((range.first + ((it - range.first) / step).roundToInt() * step).coerceIn(range))
            }, modifier = Modifier.weight(1f).padding(horizontal = 12.dp).semantics { contentDescription = title },
                valueRange = range.first.toFloat()..range.last.toFloat(), steps = ((range.last - range.first) / step - 1).coerceAtLeast(0))
            OutlinedIconButton(onClick = { change((value + step).coerceIn(range)) }, enabled = value < range.last) {
                Icon(painterResource(R.drawable.ms_add), "$title を上げる")
            }
        }
    }
}

@Composable
private fun RegionDialog(preferences: AppPreferences, state: SettingsUiState, changed: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(preferences.regionId()) }
    val regions = remember { RegionChannelCatalog.all() }
    AlertDialog(onDismissRequest = { if (!state.regionRequired) state.regionOpen = false },
        icon = { Symbol(R.drawable.ms_location_on) },
        title = { Text(if (state.regionRequired) "お住まいの地域を選択" else "放送地域") },
        text = {
            Column {
                Text("都道府県に合わせてリモコン番号と実況を対応させます。あとから変更できます。")
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(regions, key = { it.id() }) { region ->
                        var focused by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .onFocusChanged { focused = it.hasFocus }
                            .border(if (focused) 2.dp else 0.dp,
                                if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                                MaterialTheme.shapes.medium)
                            .selectable(selected == region.id(), role = Role.RadioButton, onClick = { selected = region.id() })
                            .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected == region.id(), onClick = null)
                            Spacer(Modifier.width(12.dp)); Text(region.name())
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(enabled = selected.isNotBlank(), onClick = {
            preferences.setRegionId(selected); state.regionOpen = false; changed(); state.message("放送地域を保存しました")
        }) { Text("保存") } }, dismissButton = {
            if (!state.regionRequired) TextButton(onClick = { state.regionOpen = false }) { Text("キャンセル") }
        })
}
