package com.copilotenhanced.app.settings

import android.content.Context
import com.copilotenhanced.app.llm.OpenAiCompatibleClient
import dev.ide.platform.settings.PreferenceReader
import kotlinx.coroutines.runBlocking

class ProviderConfigStore(
    private val context: Context? = null,
    private val reader: PreferenceReader? = null,
) {
    constructor(context: Context) : this(context = context, reader = null)
    constructor(reader: PreferenceReader) : this(context = null, reader = reader)

    fun load(customReader: PreferenceReader? = reader): ProviderConfig {
        val r = customReader ?: reader
        if (r != null) {
            val baseUrl = r.string(KEY_BASE_URL, "").trim()
            val apiKey = r.string(KEY_API_KEY, "").trim()
            val modelName = r.string(KEY_MODEL_NAME, "").trim()
            val tempX10 = r.int(KEY_TEMPERATURE, ProviderConfig.DEFAULT_TEMPERATURE_X10)
            val projectRoot = r.string(KEY_PROJECT_ROOT, "").trim()

            return ProviderConfig(
                baseUrl = baseUrl,
                apiKey = apiKey,
                modelName = modelName,
                temperatureX10 = tempX10,
                projectRoot = projectRoot,
            )
        }
        if (context != null) {
            return loadFromContext(context)
        }
        return ProviderConfig()
    }

    companion object {
        const val BUILD_STAMP = "2026-10-09-E"
        const val PREFS_NAME = "ai_studio_mode_prefs"
        const val KEY_BASE_URL = "baseUrl"
        const val KEY_API_KEY = "apiKey"
        const val KEY_MODEL_NAME = "modelName"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_PROJECT_ROOT = "projectRoot"
        const val KEY_TEST_CONNECTION = "testConnection"

        fun read(reader: PreferenceReader): ProviderConfig = ProviderConfigStore(reader = reader).load()

        fun loadFromContext(context: Context): ProviderConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val baseUrl = prefs.getString(KEY_BASE_URL, "")?.trim() ?: ""
            val apiKey = prefs.getString(KEY_API_KEY, "")?.trim() ?: ""
            val modelName = prefs.getString(KEY_MODEL_NAME, "")?.trim() ?: ""
            val tempX10 = prefs.getInt(KEY_TEMPERATURE, ProviderConfig.DEFAULT_TEMPERATURE_X10)
            val projectRoot = prefs.getString(KEY_PROJECT_ROOT, "")?.trim() ?: ""

            return ProviderConfig(
                baseUrl = baseUrl,
                apiKey = apiKey,
                modelName = modelName,
                temperatureX10 = tempX10,
                projectRoot = projectRoot,
            )
        }

        fun saveToContext(context: Context, config: ProviderConfig) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, config.baseUrl.trim())
                .putString(KEY_API_KEY, config.apiKey.trim())
                .putString(KEY_MODEL_NAME, config.modelName.trim())
                .putInt(KEY_TEMPERATURE, config.temperatureX10)
                .putString(KEY_PROJECT_ROOT, config.projectRoot.trim())
                .apply()
        }

        fun testConnection(config: ProviderConfig): String {
            return runBlocking {
                OpenAiCompatibleClient(config).testConnection()
            }
        }
    }
}
