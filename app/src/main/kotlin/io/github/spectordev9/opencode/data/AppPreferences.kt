package io.github.spectordev9.opencode.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * UI-only preferences. Provider credentials, loopback secrets, and runtime
 * settings must never live here.
 */
class AppPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "opencode_ui_preferences",
        Context.MODE_PRIVATE,
    )
    private val mutableThemeMode = MutableStateFlow(
        ThemeMode.fromStorage(preferences.getString(KEY_THEME_MODE, null)),
    )

    val themeMode: StateFlow<ThemeMode> = mutableThemeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
        mutableThemeMode.value = mode
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
