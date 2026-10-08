package com.copilotenhanced.app.settings

data class ProviderConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val temperatureX10: Int = DEFAULT_TEMPERATURE_X10,
    val projectRoot: String = "",
) {
    val temperature: Float
        get() = temperatureX10 / 10f

    companion object {
        const val DEFAULT_TEMPERATURE_X10 = 7
    }
}
