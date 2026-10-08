package com.copilotenhanced.app

import com.copilotenhanced.app.settings.AiStudioSettingsPage
import dev.ide.platform.settings.SETTINGS_PAGE_EP
import dev.ide.plugin.Plugin
import dev.ide.plugin.PluginRegistration

class CopilotEnhancedPlugin : Plugin {

    override fun register(reg: PluginRegistration) {
        reg.register(SETTINGS_PAGE_EP, AiStudioSettingsPage())
    }
}
