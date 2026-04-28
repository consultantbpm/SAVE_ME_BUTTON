package com.savemebutton.phone.data

import android.content.Context
import com.savemebutton.shared.SaveMeJson
import com.savemebutton.shared.SosConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val PREFS = "savemebutton_prefs"
private const val KEY_CONFIG = "config_json"

class ConfigRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<SosConfig> = _config

    private fun load(): SosConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return SosConfig().normalized()
        return runCatching { SaveMeJson.decodeFromString<SosConfig>(raw) }
            .getOrDefault(SosConfig())
            .normalized()
    }

    fun save(config: SosConfig) {
        val normalized = config.normalized()
        prefs.edit().putString(KEY_CONFIG, SaveMeJson.encodeToString(SosConfig.serializer(), normalized)).apply()
        _config.value = normalized
    }
}
