package com.yukkurimatchatea.nxdanmakutv

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test

class MaterialSettingsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setup(firstRun: Boolean = false) {
        context.getSharedPreferences("nx_danmaku_tv", 0).edit().clear()
            .putBoolean("auto_update_check", false)
            .apply { if (!firstRun) putString("region_id", "ishikawa") }.commit()
    }

    @Test fun firstRunRegionIsSavedWithoutRecreatingActivity() {
        setup(firstRun = true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val original = AtomicReference<MainActivity>()
            scenario.onActivity { original.set(it) }
            compose.onNodeWithText("お住まいの地域を選択").assertIsDisplayed()
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("東京都"))
            compose.onNodeWithText("東京都").performClick()
            compose.onNodeWithText("保存").performClick()
            compose.onNodeWithText("お住まいの地域を選択").assertDoesNotExist()
            compose.onNodeWithText("東京都").assertIsDisplayed()
            assertEquals("tokyo", AppPreferences(context).regionId())
            scenario.onActivity { assertFalse(it.isFinishing); assertSame(original.get(), it) }
        }
    }

    @Test fun displayChangesPersistAndCanBeReset() {
        setup()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("表示調整", useUnmergedTree = true).performClick()
            compose.onNodeWithContentDescription("文字サイズ を上げる").performScrollTo().performClick()
            assertEquals(1.1f, AppPreferences(context).textScale(), .001f)
            compose.onNodeWithText("表示設定を初期値に戻す").performScrollTo().performClick()
            compose.onNodeWithText("リセット", substring = false).performClick()
            assertEquals(1f, AppPreferences(context).textScale(), .001f)
        }
    }

    @Test fun themeSelectionAndRegionChangeSurviveRecreation() {
        setup()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithText("設定", useUnmergedTree = true).performClick()
            compose.onNodeWithText("ダーク").performClick()
            assertEquals("dark", context.getSharedPreferences("nx_danmaku_tv", 0).getString("ui_theme", ""))
            compose.onNodeWithText("ライト").performClick()
            scenario.recreate()
            compose.onNodeWithText("ライト").assertIsSelected()
            compose.onNodeWithContentDescription("テレビ画面へ戻る").performClick()
            compose.onNodeWithText("放送地域").performScrollTo().performClick()
            compose.onNodeWithText("北海道").performScrollTo().performClick()
            compose.onNodeWithText("保存").performClick()
            assertEquals("hokkaido", AppPreferences(context).regionId())
        }
    }
}
