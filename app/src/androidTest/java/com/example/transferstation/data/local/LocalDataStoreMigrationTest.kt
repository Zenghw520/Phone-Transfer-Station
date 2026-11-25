package com.example.transferstation.data.local

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.transferstation.domain.model.AppearanceSettings
import com.example.transferstation.domain.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** 验证外观、会话摘要和发送确认的持久化，以及旧版数据迁移。 */
@RunWith(AndroidJUnit4::class)
class LocalDataStoreMigrationTest {
    /** 重启本地数据库后仍能读取外观偏好和会话摘要。 */
    @Test
    fun appearanceAndSessionSummarySurviveDatabaseReopen() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "appearance-${UUID.randomUUID()}.db"
        val context = IsolatedPreferencesContext(appContext, databaseName)
        var store = LocalDataStore(context, databaseName)
        val session = store.createSession()
        val appearance = AppearanceSettings(
            themeMode = "dark",
            monetColorEnabled = true,
            accentColorHex = "#7654AB",
            liquidGlassEnabled = true,
            inputLiquidGlassEnabled = true,
            scaleFactor = 1.15f,
        )
        store.saveSessionSummary(session.id, "已保存的对话摘要")
        store.saveAppearanceSettings(appearance)
        store.close()

        store = LocalDataStore(context, databaseName)
        assertEquals("已保存的对话摘要", store.getSessions().single().summary)
        assertEquals(appearance, store.getAppearanceSettings())

        store.close()
        context.deleteDatabase(databaseName)
        context.getSharedPreferences("chat_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** 新安装尚未确认，确认后更换模型和 API 并重开存储时沿用该确认。 */
    @Test
    fun transferConfirmationSurvivesModelAndApiChanges() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "transfer-confirmation-${UUID.randomUUID()}.db"
        val context = IsolatedPreferencesContext(appContext, databaseName)
        try {
            LocalDataStore(context, databaseName).use { store ->
                assertFalse(store.getPreferences().hasConfirmedMessageTransfer)
                store.confirmMessageTransfer()
                store.savePreferences(UserPreferences(selectedProviderId = "first-api", selectedModelId = "first-model"))
                store.savePreferences(UserPreferences(selectedProviderId = "second-api", selectedModelId = "second-model"))
            }
            LocalDataStore(context, databaseName).use { store ->
                val saved = store.getPreferences()
                assertTrue(saved.hasConfirmedMessageTransfer)
                assertEquals("second-api", saved.selectedProviderId)
                assertEquals("second-model", saved.selectedModelId)
            }
        } finally {
            context.deleteDatabase(databaseName)
            context.getSharedPreferences("chat_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    /** 升级后，即使原服务商已删除，也沿用旧版对任一服务商的确认。 */
    @Test
    fun legacyProviderConfirmationCountsAsGlobalConfirmation() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "legacy-transfer-confirmation-${UUID.randomUUID()}.db"
        val context = IsolatedPreferencesContext(appContext, databaseName)
        val preferences = context.getSharedPreferences("chat_preferences", Context.MODE_PRIVATE)
        try {
            preferences.edit().putStringSet("confirmed_provider_ids", setOf("removed-api")).commit()
            LocalDataStore(context, databaseName).use { store ->
                assertTrue(store.getPreferences().hasConfirmedMessageTransfer)
            }
        } finally {
            context.deleteDatabase(databaseName)
            preferences.edit().clear().commit()
        }
    }

    /** 升级旧数据库时保留会话内容并导入 SharedPreferences 中的主题模式。 */
    @Test
    fun versionOneDatabaseMigratesWithoutLosingSessionsOrTheme() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-${UUID.randomUUID()}.db"
        val context = IsolatedPreferencesContext(appContext, databaseName)
        context.getSharedPreferences("chat_preferences", Context.MODE_PRIVATE)
            .edit().putString("theme_mode", "dark").commit()
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL("CREATE TABLE sessions (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
            database.execSQL("CREATE TABLE tasks (id TEXT PRIMARY KEY NOT NULL)")
            database.execSQL("INSERT INTO sessions (id, title, created_at, updated_at) VALUES ('session-old', '旧会话', 1, 2)")
            database.execSQL("PRAGMA user_version = 1")
        }

        val store = LocalDataStore(context, databaseName)
        assertEquals("旧会话", store.getSessions().single().title)
        assertNull(store.getSessions().single().summary)
        assertEquals("dark", store.getAppearanceSettings().themeMode)
        store.close()

        context.deleteDatabase(databaseName)
        context.getSharedPreferences("chat_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }
}

/** 为仪器测试隔离数据库旁的 SharedPreferences 文件名。 */
private class IsolatedPreferencesContext(base: Context, private val suffix: String) : ContextWrapper(base) {
    /** 将测试偏好写入独立名称，避免影响应用的真实用户设置。 */
    override fun getSharedPreferences(name: String, mode: Int) =
        baseContext.getSharedPreferences("$suffix-$name", mode)
}
