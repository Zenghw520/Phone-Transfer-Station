package com.example.transferstation.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.transferstation.domain.model.ChatMessage
import com.example.transferstation.domain.model.ChatSession
import com.example.transferstation.domain.model.AppearanceSettings
import com.example.transferstation.domain.model.GenerationState
import com.example.transferstation.domain.model.GenerationTask
import com.example.transferstation.domain.model.MessageRole
import com.example.transferstation.domain.model.ModelInfo
import com.example.transferstation.domain.model.ProviderConnection
import com.example.transferstation.domain.model.ProviderKind
import com.example.transferstation.domain.model.UserPreferences
import org.json.JSONArray
import java.io.Closeable
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 负责将会话、消息、服务商配置和生成任务持久化到应用私有数据库。 */
class LocalDataStore(context: Context, databaseName: String = DATABASE_NAME) : SQLiteOpenHelper(context, databaseName, null, DATABASE_VERSION), Closeable {
    private val preferences = context.getSharedPreferences("chat_preferences", Context.MODE_PRIVATE)

    /** 初始化数据库表结构，服务商密钥使用独立 Keystore 密钥加密。 */
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL("CREATE TABLE providers (id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, base_url TEXT NOT NULL, api_key TEXT NOT NULL, custom_images INTEGER NOT NULL, reasoning_levels TEXT NOT NULL, models TEXT NOT NULL, validated_at INTEGER)")
        database.execSQL("CREATE TABLE sessions (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, summary TEXT)")
        database.execSQL("CREATE TABLE messages (id TEXT PRIMARY KEY NOT NULL, session_id TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, images TEXT NOT NULL, created_at INTEGER NOT NULL, provider_name TEXT, model_name TEXT, reasoning_level TEXT, reasoning_summary TEXT, duration_ms INTEGER, task_id TEXT)")
        database.execSQL("CREATE INDEX messages_session_index ON messages(session_id, created_at)")
        database.execSQL("CREATE TABLE tasks (id TEXT PRIMARY KEY NOT NULL, session_id TEXT NOT NULL, user_message_id TEXT NOT NULL, assistant_message_id TEXT NOT NULL, provider_id TEXT NOT NULL, provider_kind TEXT NOT NULL, provider_name TEXT NOT NULL, base_url TEXT NOT NULL, model_id TEXT NOT NULL, model_name TEXT NOT NULL, supports_images INTEGER NOT NULL, api_key TEXT NOT NULL, reasoning_level TEXT, state TEXT NOT NULL, assistant_text TEXT NOT NULL, reasoning_summary TEXT, started_at INTEGER, completed_at INTEGER, error_message TEXT, output_token_count INTEGER NOT NULL DEFAULT 0, output_tokens_per_second REAL, token_rate_estimated INTEGER NOT NULL DEFAULT 0)")
        database.execSQL("CREATE INDEX tasks_state_index ON tasks(state)")
        createAppearanceSettingsTable(database)
    }

    /** 迁移旧会话摘要、任务计数列和 SharedPreferences 中的主题模式。 */
    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.execSQL("ALTER TABLE sessions ADD COLUMN summary TEXT")
            database.execSQL("ALTER TABLE tasks ADD COLUMN output_token_count INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE tasks ADD COLUMN output_tokens_per_second REAL")
            database.execSQL("ALTER TABLE tasks ADD COLUMN token_rate_estimated INTEGER NOT NULL DEFAULT 0")
            createAppearanceSettingsTable(database, preferences.getString("theme_mode", "system") ?: "system")
        }
    }

    /** 新增或更新服务商连接，并在写入 SQLite 之前加密 API Key。 */
    @Synchronized
    fun saveConnection(connection: ProviderConnection) {
        val values = ContentValues().apply {
            put("id", connection.id)
            put("kind", connection.kind.name)
            put("name", connection.name)
            put("base_url", connection.baseUrl)
            put("api_key", encrypt(connection.apiKey))
            put("custom_images", if (connection.customSupportsImages) 1 else 0)
            put("reasoning_levels", JSONArray(connection.customReasoningLevels).toString())
            put("models", modelsToJson(connection.models))
            connection.lastValidatedAt?.let { put("validated_at", it) } ?: putNull("validated_at")
        }
        writableDatabase.insertWithOnConflict("providers", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** 读取所有服务商连接并在内存中解密 API Key。 */
    @Synchronized
    fun getConnections(): List<ProviderConnection> = readableDatabase.rawQuery("SELECT * FROM providers ORDER BY name COLLATE NOCASE", null).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(connectionFrom(cursor))
        }
    }

    /** 根据配置标识查找服务商连接。 */
    @Synchronized
    fun getConnection(id: String): ProviderConnection? = readableDatabase.rawQuery("SELECT * FROM providers WHERE id = ?", arrayOf(id)).use { cursor ->
        if (cursor.moveToFirst()) connectionFrom(cursor) else null
    }

    /** 删除服务商配置，保留已经提交的历史会话和任务快照。 */
    @Synchronized
    fun deleteConnection(id: String) {
        writableDatabase.delete("providers", "id = ?", arrayOf(id))
    }

    /** 保存或更新服务商可用模型及其能力信息。 */
    @Synchronized
    fun updateConnectionModels(id: String, models: List<ModelInfo>, validatedAt: Long) {
        val values = ContentValues().apply {
            put("models", modelsToJson(models))
            put("validated_at", validatedAt)
        }
        writableDatabase.update("providers", values, "id = ?", arrayOf(id))
    }

    /** 创建空白会话并返回其持久化记录。 */
    @Synchronized
    fun createSession(): ChatSession {
        val now = System.currentTimeMillis()
        return ChatSession(UUID.randomUUID().toString(), "新对话", now, now).also(::saveSession)
    }

    /** 保存会话标题、摘要及最近更新时间。 */
    @Synchronized
    fun saveSession(session: ChatSession) {
        val values = ContentValues().apply {
            put("id", session.id)
            put("title", session.title)
            put("created_at", session.createdAt)
            put("updated_at", session.updatedAt)
            session.summary?.let { put("summary", it) } ?: putNull("summary")
        }
        writableDatabase.insertWithOnConflict("sessions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** 更新会话列表显示的 AI 摘要或最近消息预览。 */
    @Synchronized
    fun saveSessionSummary(sessionId: String, summary: String) {
        writableDatabase.execSQL("UPDATE sessions SET summary = ? WHERE id = ?", arrayOf(summary, sessionId))
    }

    /** 按最近更新时间倒序读取全部会话。 */
    @Synchronized
    fun getSessions(): List<ChatSession> = readableDatabase.rawQuery("SELECT * FROM sessions ORDER BY updated_at DESC", null).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(
                ChatSession(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getLong(3), cursor.stringOrNull(4)),
            )
        }
    }

    /** 删除会话及其本地消息，并清理该会话关联任务。 */
    @Synchronized
    fun deleteSession(sessionId: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("tasks", "session_id = ?", arrayOf(sessionId))
            writableDatabase.delete("messages", "session_id = ?", arrayOf(sessionId))
            writableDatabase.delete("sessions", "id = ?", arrayOf(sessionId))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    /** 保存消息正文并刷新最近消息预览及会话更新时间。 */
    @Synchronized
    fun saveMessage(message: ChatMessage) {
        val values = ContentValues().apply {
            put("id", message.id)
            put("session_id", message.sessionId)
            put("role", message.role.name)
            put("content", message.text)
            put("images", JSONArray(message.imagePaths).toString())
            put("created_at", message.createdAt)
            message.providerName?.let { put("provider_name", it) } ?: putNull("provider_name")
            message.modelName?.let { put("model_name", it) } ?: putNull("model_name")
            message.reasoningLevel?.let { put("reasoning_level", it) } ?: putNull("reasoning_level")
            message.reasoningSummary?.let { put("reasoning_summary", it) } ?: putNull("reasoning_summary")
            message.requestDurationMs?.let { put("duration_ms", it) } ?: putNull("duration_ms")
            message.taskId?.let { put("task_id", it) } ?: putNull("task_id")
        }
        writableDatabase.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        val preview = if (message.role == MessageRole.USER) {
            message.text.replace(Regex("\\s+"), " ").trim().take(120).ifBlank {
                if (message.imagePaths.isNotEmpty()) "图片消息" else ""
            }
        } else {
            null
        }
        writableDatabase.execSQL(
            "UPDATE sessions SET updated_at = ?, title = CASE WHEN title = '新对话' AND ? <> '' THEN ? ELSE title END, summary = COALESCE(?, summary) WHERE id = ?",
            arrayOf<Any?>(message.createdAt, message.text, message.text.take(56), preview, message.sessionId),
        )
    }

    /** 读取单个会话的全部消息并按创建时间排序。 */
    @Synchronized
    fun getMessages(sessionId: String): List<ChatMessage> = readableDatabase.rawQuery(
        "SELECT * FROM messages WHERE session_id = ? ORDER BY created_at ASC, rowid ASC",
        arrayOf(sessionId),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(
                ChatMessage(
                    id = cursor.getString(0),
                    sessionId = cursor.getString(1),
                    role = MessageRole.valueOf(cursor.getString(2)),
                    text = cursor.getString(3),
                    imagePaths = JSONArray(cursor.getString(4)).let { array -> List(array.length()) { index -> array.getString(index) } },
                    createdAt = cursor.getLong(5),
                    providerName = cursor.stringOrNull(6),
                    modelName = cursor.stringOrNull(7),
                    reasoningLevel = cursor.stringOrNull(8),
                    reasoningSummary = cursor.stringOrNull(9),
                    requestDurationMs = cursor.longOrNull(10),
                    taskId = cursor.stringOrNull(11),
                ),
            )
        }
    }

    /** 将回答任务快照加密后保存，支持前台服务在应用退到后台后继续运行。 */
    @Synchronized
    fun saveTask(task: GenerationTask) {
        val values = ContentValues().apply {
            put("id", task.id)
            put("session_id", task.sessionId)
            put("user_message_id", task.userMessageId)
            put("assistant_message_id", task.assistantMessageId)
            put("provider_id", task.providerId)
            put("provider_kind", task.providerKind.name)
            put("provider_name", task.providerName)
            put("base_url", task.baseUrl)
            put("model_id", task.modelId)
            put("model_name", task.modelName)
            put("supports_images", if (task.supportsImages) 1 else 0)
            put("api_key", encrypt(task.apiKey))
            task.reasoningLevel?.let { put("reasoning_level", it) } ?: putNull("reasoning_level")
            put("state", task.state.name)
            put("assistant_text", task.assistantText)
            task.reasoningSummary?.let { put("reasoning_summary", it) } ?: putNull("reasoning_summary")
            task.startedAt?.let { put("started_at", it) } ?: putNull("started_at")
            task.completedAt?.let { put("completed_at", it) } ?: putNull("completed_at")
            task.errorMessage?.let { put("error_message", it) } ?: putNull("error_message")
            put("output_token_count", task.outputTokenCount)
            task.outputTokensPerSecond?.let { put("output_tokens_per_second", it) } ?: putNull("output_tokens_per_second")
            put("token_rate_estimated", if (task.tokenRateEstimated) 1 else 0)
        }
        writableDatabase.insertWithOnConflict("tasks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** 根据任务标识读取任务快照，并只在内存中解密其 API Key。 */
    @Synchronized
    fun getTask(taskId: String): GenerationTask? = readableDatabase.rawQuery("SELECT * FROM tasks WHERE id = ?", arrayOf(taskId)).use { cursor ->
        if (cursor.moveToFirst()) taskFrom(cursor) else null
    }

    /** 读取全部任务，以便状态栏展示排队和运行状态。 */
    @Synchronized
    fun getTasks(): List<GenerationTask> = readableDatabase.rawQuery("SELECT * FROM tasks ORDER BY rowid ASC", null).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(taskFrom(cursor))
        }
    }

    /** 将进程结束前未完成的任务统一标记为中断，防止自动重复请求产生费用。 */
    @Synchronized
    fun markUnfinishedTasksInterrupted() {
        writableDatabase.execSQL(
            "UPDATE tasks SET state = ?, error_message = ? WHERE state IN (?, ?, ?)",
            arrayOf(GenerationState.INTERRUPTED.name, "应用进程结束，任务已中断，可手动重试。", GenerationState.QUEUED.name, GenerationState.CONNECTING.name, GenerationState.STREAMING.name),
        )
    }

    /** 保存当前模型选择，并将主题偏好单独保存到 SQLite。 */
    @Synchronized
    fun savePreferences(value: UserPreferences) {
        preferences.edit()
            .putString("selected_provider_id", value.selectedProviderId)
            .putString("selected_model_id", value.selectedModelId)
            .apply()
        saveAppearanceSettings(getAppearanceSettings().copy(themeMode = value.themeMode))
    }

    /** 读取模型选择、全局发送确认及 SQLite 中的主题模式。 */
    @Synchronized
    fun getPreferences(): UserPreferences = UserPreferences(
        selectedProviderId = preferences.getString("selected_provider_id", null),
        selectedModelId = preferences.getString("selected_model_id", null),
        themeMode = getAppearanceSettings().themeMode,
        hasConfirmedMessageTransfer = hasConfirmedMessageTransfer(),
    )

    /** 读取全局发送确认，并沿用旧版对任一服务商的确认记录。 */
    fun hasConfirmedMessageTransfer(): Boolean =
        preferences.getBoolean("message_transfer_confirmed", false) ||
            preferences.getStringSet("confirmed_provider_ids", emptySet()).orEmpty().isNotEmpty()

    /** 保存全局发送确认，模型选择及服务商配置的写入不会覆盖它。 */
    fun confirmMessageTransfer() {
        preferences.edit().putBoolean("message_transfer_confirmed", true).apply()
    }

    /** 保存完整外观设置并即时供后续界面快照读取。 */
    @Synchronized
    fun saveAppearanceSettings(value: AppearanceSettings) {
        val settings = ContentValues().apply {
            put("id", 1)
            put("theme_mode", value.themeMode)
            put("monet_enabled", if (value.monetColorEnabled) 1 else 0)
            put("accent_color_hex", value.accentColorHex)
            put("liquid_glass_enabled", if (value.liquidGlassEnabled) 1 else 0)
            put("input_glass_enabled", if (value.inputLiquidGlassEnabled) 1 else 0)
            put("scale_factor", value.scaleFactor)
        }
        writableDatabase.insertWithOnConflict("appearance_settings", null, settings, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** 读取持久化的外观偏好，缺少记录时返回应用默认外观。 */
    @Synchronized
    fun getAppearanceSettings(): AppearanceSettings = readableDatabase.rawQuery(
        "SELECT theme_mode, monet_enabled, accent_color_hex, liquid_glass_enabled, input_glass_enabled, scale_factor FROM appearance_settings WHERE id = 1",
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use AppearanceSettings()
        AppearanceSettings(
            themeMode = cursor.getString(0),
            monetColorEnabled = cursor.getInt(1) != 0,
            accentColorHex = cursor.getString(2),
            liquidGlassEnabled = cursor.getInt(3) != 0,
            inputLiquidGlassEnabled = cursor.getInt(4) != 0,
            scaleFactor = cursor.getFloat(5),
        )
    }

    /** 创建外观设置表并导入首次安装或旧版偏好中的主题模式。 */
    private fun createAppearanceSettingsTable(database: SQLiteDatabase, themeMode: String = "system") {
        database.execSQL(
            "CREATE TABLE appearance_settings (id INTEGER PRIMARY KEY NOT NULL CHECK(id = 1), theme_mode TEXT NOT NULL, monet_enabled INTEGER NOT NULL, accent_color_hex TEXT NOT NULL, liquid_glass_enabled INTEGER NOT NULL, input_glass_enabled INTEGER NOT NULL, scale_factor REAL NOT NULL)",
        )
        val defaults = ContentValues().apply {
            put("id", 1)
            put("theme_mode", themeMode)
            put("monet_enabled", 0)
            put("accent_color_hex", "#315FBA")
            put("liquid_glass_enabled", 0)
            put("input_glass_enabled", 0)
            put("scale_factor", 1f)
        }
        database.insertOrThrow("appearance_settings", null, defaults)
    }

    /** 确保应用私有 Android Keystore 中存在 AES-256 加密密钥。 */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            val isUsable = runCatching {
                Cipher.getInstance(TRANSFORMATION).init(Cipher.ENCRYPT_MODE, existingKey)
            }.isSuccess
            if (isUsable) return existingKey
            keyStore.deleteEntry(KEY_ALIAS)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    /** 使用 Keystore 密钥和随机 GCM IV 加密密文并编码为可持久化文本。 */
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /** 在内存中解密 API Key，并在密钥失效时返回明确错误。 */
    private fun decrypt(value: String): String {
        return try {
            val combined = Base64.decode(value, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
                String(doFinal(ciphertext), Charsets.UTF_8)
            }
        } catch (exception: Exception) {
            throw IllegalStateException("无法解密 API Key，请重新录入该服务商密钥。", exception)
        }
    }

    /** 从 SQLite 游标构建服务商连接并解密其模型目录和 API Key。 */
    private fun connectionFrom(cursor: android.database.Cursor): ProviderConnection {
        val apiKey = runCatching { decrypt(cursor.getString(4)) }.getOrDefault("")
        return ProviderConnection(
            id = cursor.getString(0),
            kind = ProviderKind.valueOf(cursor.getString(1)),
            name = cursor.getString(2),
            baseUrl = cursor.getString(3),
            apiKey = apiKey,
            customSupportsImages = cursor.getInt(5) != 0,
            customReasoningLevels = jsonToStrings(cursor.getString(6)),
            models = jsonToModels(cursor.getString(7)),
            lastValidatedAt = cursor.longOrNull(8),
        )
    }

    /** 从 SQLite 游标构建已提交任务，并解密任务使用的 API Key 快照。 */
    private fun taskFrom(cursor: android.database.Cursor): GenerationTask {
        val apiKey = runCatching { decrypt(cursor.getString(11)) }.getOrDefault("")
        return GenerationTask(
            id = cursor.getString(0),
            sessionId = cursor.getString(1),
            userMessageId = cursor.getString(2),
            assistantMessageId = cursor.getString(3),
            providerId = cursor.getString(4),
            providerKind = ProviderKind.valueOf(cursor.getString(5)),
            providerName = cursor.getString(6),
            baseUrl = cursor.getString(7),
            modelId = cursor.getString(8),
            modelName = cursor.getString(9),
            supportsImages = cursor.getInt(10) != 0,
            apiKey = apiKey,
            reasoningLevel = cursor.stringOrNull(12),
            state = GenerationState.valueOf(cursor.getString(13)),
            assistantText = cursor.getString(14),
            reasoningSummary = cursor.stringOrNull(15),
            startedAt = cursor.longOrNull(16),
            completedAt = cursor.longOrNull(17),
            errorMessage = cursor.stringOrNull(18),
            outputTokenCount = cursor.getInt(19),
            outputTokensPerSecond = if (cursor.isNull(20)) null else cursor.getDouble(20),
            tokenRateEstimated = cursor.getInt(21) != 0,
        )
    }

    /** 将模型能力目录编码为 UTF-8 JSON 字符串。 */
    private fun modelsToJson(models: List<ModelInfo>): String = JSONArray().apply {
        models.forEach { model ->
            put(org.json.JSONObject().apply {
                put("id", model.id)
                put("name", model.name)
                put("images", model.supportsImages)
                put("reasoning", JSONArray(model.reasoningLevels))
                put("source", model.capabilitySource)
            })
        }
    }.toString()

    /** 从 JSON 读取模型能力目录，忽略格式不完整的单条模型记录。 */
    private fun jsonToModels(value: String): List<ModelInfo> = runCatching {
        val array = JSONArray(value)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    ModelInfo(
                        id = item.getString("id"),
                        name = item.optString("name", item.getString("id")),
                        supportsImages = item.optBoolean("images"),
                        reasoningLevels = jsonToStrings(item.optJSONArray("reasoning")?.toString().orEmpty()),
                        capabilitySource = item.optString("source", "服务商目录"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    /** 将 JSON 字符串数组解码为列表，错误或空值返回空列表。 */
    private fun jsonToStrings(value: String): List<String> = runCatching {
        val array = JSONArray(value)
        List(array.length()) { index -> array.getString(index) }
    }.getOrDefault(emptyList())

    /** 安全读取 SQLite 中可能为空的字符串列。 */
    private fun android.database.Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

    /** 安全读取 SQLite 中可能为空的整数列。 */
    private fun android.database.Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

    /** 释放 SQLite 数据库资源。 */
    override fun close() {
        super.close()
    }

    private companion object {
        const val DATABASE_NAME = "transfer_station.db"
        const val DATABASE_VERSION = 2
        const val KEY_ALIAS = "transfer_station_api_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 128
    }
}
