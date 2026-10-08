package com.copilotenhanced.app.settings

import com.copilotenhanced.app.llm.OpenAiCompatibleClient
import dev.ide.platform.settings.PreferenceReader
import dev.ide.platform.settings.SettingControl
import dev.ide.platform.settings.SettingsPage
import dev.ide.platform.settings.SettingsScope
import kotlinx.coroutines.runBlocking

class AiStudioSettingsPage : SettingsPage {

    override val id: String = "ai-studio-mode"
    override val title: String = "AI Studio Mode"
    override val iconId: String = "sparkle"
    override val order: Int = 50
    override val scope: SettingsScope = SettingsScope.APPLICATION

    override fun controls(): List<SettingControl> = listOf(
        SettingControl.Text(
            key = ProviderConfigStore.KEY_BASE_URL,
            title = "Base URL",
            description = "OpenAI-compatible endpoint base URL",
            default = "",
            placeholder = "https://...",
        ),
        SettingControl.Text(
            key = ProviderConfigStore.KEY_API_KEY,
            title = "API Key",
            description = "API key for your provider",
            default = "",
            placeholder = "sk-...",
        ),
        SettingControl.Text(
            key = ProviderConfigStore.KEY_MODEL_NAME,
            title = "Model name",
            description = "Name of the model to use",
            default = "",
            placeholder = "model-name",
        ),
        SettingControl.Text(
            key = ProviderConfigStore.KEY_PROJECT_ROOT,
            title = "Project root folder (absolute path)",
            description = "Absolute path to the project root (auto-detection takes priority)",
            default = "",
            placeholder = "/storage/emulated/0/...",
        ),
        SettingControl.IntSlider(
            key = ProviderConfigStore.KEY_TEMPERATURE,
            title = "Temperature x10",
            description = "Generation temperature scaled by 10 (0 = 0.0, 20 = 2.0)",
            default = 7,
            min = 0,
            max = 20,
            step = 1,
        ),
        SettingControl.Action(
            key = ProviderConfigStore.KEY_TEST_CONNECTION,
            title = "Test connection",
            description = "Send a test request to verify the configuration",
            buttonLabel = "Test connection",
        ),
        SettingControl.Action(
            key = "manage_skills",
            title = "Manage skills",
            description = "Manage imported markdown skills for AI Studio Mode",
            buttonLabel = "Manage skills",
        ),
        SettingControl.Text(
            key = "build_stamp_display",
            title = "Build",
            description = "Build: ${ProviderConfigStore.BUILD_STAMP}",
            default = ProviderConfigStore.BUILD_STAMP,
            placeholder = "",
        ),
    )

    override fun onAction(actionKey: String, prefs: PreferenceReader): String? {
        if (actionKey == ProviderConfigStore.KEY_TEST_CONNECTION) {
            val store = ProviderConfigStore(reader = prefs)
            return runBlocking {
                OpenAiCompatibleClient(configStore = store).testConnection()
            }
        }
        if (actionKey == "manage_skills") {
            return "Manage skills screen can be opened from the AI Studio chat top bar (Skills button) or registered screen ai-studio-mode.skills."
        }
        return null
    }
}
