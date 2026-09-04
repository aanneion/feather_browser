package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.browser.AppThemeMode
import com.example.browser.DownloadProvider
import com.example.browser.HttpsMode
import com.example.browser.NewTabStyle
import com.example.browser.SearchEngine

class BrowserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "feather_browser_preferences"
        private const val KEY_SEARCH_ENGINE = "pref_search_engine"
        private const val KEY_THEME_MODE = "pref_theme_mode"
        private const val KEY_USE_MATERIAL_YOU = "pref_use_material_you"
        private const val KEY_NEW_TAB_STYLE = "pref_new_tab_style"
        private const val KEY_AD_BLOCK_ENABLED = "pref_ad_block_enabled"
        private const val KEY_BLOCK_COOKIES = "pref_block_third_party_cookies"
        private const val KEY_HTTPS_MODE = "pref_https_mode"
        private const val KEY_WEB_DARK_MODE = "pref_web_dark_mode"
        private const val KEY_BACKGROUND_PLAY = "pref_background_play"
        private const val KEY_DOWNLOAD_PROVIDER = "pref_download_provider"
        private const val KEY_WEATHER_ON_NEW_TAB = "pref_weather_on_new_tab"
        private const val KEY_WEATHER_FAHRENHEIT = "pref_weather_fahrenheit"
    }

    fun isWeatherOnNewTab(): Boolean {
        return prefs.getBoolean(KEY_WEATHER_ON_NEW_TAB, true)
    }

    fun setWeatherOnNewTab(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEATHER_ON_NEW_TAB, enabled).apply()
    }

    fun isWeatherFahrenheit(): Boolean {
        return prefs.getBoolean(KEY_WEATHER_FAHRENHEIT, false)
    }

    fun setWeatherFahrenheit(fahrenheit: Boolean) {
        prefs.edit().putBoolean(KEY_WEATHER_FAHRENHEIT, fahrenheit).apply()
    }

    fun getSearchEngine(): SearchEngine {
        val name = prefs.getString(KEY_SEARCH_ENGINE, SearchEngine.GOOGLE.name)
        return try {
            SearchEngine.valueOf(name ?: SearchEngine.GOOGLE.name)
        } catch (e: Exception) {
            SearchEngine.GOOGLE
        }
    }

    fun setSearchEngine(engine: SearchEngine) {
        prefs.edit().putString(KEY_SEARCH_ENGINE, engine.name).apply()
    }

    fun getThemeMode(): AppThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        return try {
            AppThemeMode.valueOf(name ?: AppThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getUseMaterialYou(): Boolean {
        return prefs.getBoolean(KEY_USE_MATERIAL_YOU, true)
    }

    fun setUseMaterialYou(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_MATERIAL_YOU, enabled).apply()
    }

    fun getNewTabStyle(): NewTabStyle {
        val name = prefs.getString(KEY_NEW_TAB_STYLE, NewTabStyle.PRODUCTIVITY.name)
        return try {
            NewTabStyle.valueOf(name ?: NewTabStyle.PRODUCTIVITY.name)
        } catch (e: Exception) {
            NewTabStyle.PRODUCTIVITY
        }
    }

    fun setNewTabStyle(style: NewTabStyle) {
        prefs.edit().putString(KEY_NEW_TAB_STYLE, style.name).apply()
    }

    fun isAdBlockEnabled(): Boolean {
        return prefs.getBoolean(KEY_AD_BLOCK_ENABLED, true)
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AD_BLOCK_ENABLED, enabled).apply()
    }

    fun isBlockThirdPartyCookies(): Boolean {
        return prefs.getBoolean(KEY_BLOCK_COOKIES, true)
    }

    fun setBlockThirdPartyCookies(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCK_COOKIES, enabled).apply()
    }

    fun getHttpsMode(): HttpsMode {
        val name = prefs.getString(KEY_HTTPS_MODE, HttpsMode.PREFER_HTTPS.name)
        return try {
            HttpsMode.valueOf(name ?: HttpsMode.PREFER_HTTPS.name)
        } catch (e: Exception) {
            HttpsMode.PREFER_HTTPS
        }
    }

    fun setHttpsMode(mode: HttpsMode) {
        prefs.edit().putString(KEY_HTTPS_MODE, mode.name).apply()
    }

    fun isEnableWebDarkMode(): Boolean {
        return prefs.getBoolean(KEY_WEB_DARK_MODE, false)
    }

    fun setEnableWebDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEB_DARK_MODE, enabled).apply()
    }

    fun isEnableBackgroundPlay(): Boolean {
        return prefs.getBoolean(KEY_BACKGROUND_PLAY, true)
    }

    fun setEnableBackgroundPlay(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_PLAY, enabled).apply()
    }

    fun getDownloadProvider(): DownloadProvider {
        val name = prefs.getString(KEY_DOWNLOAD_PROVIDER, DownloadProvider.BUILT_IN.name)
        return try {
            DownloadProvider.valueOf(name ?: DownloadProvider.BUILT_IN.name)
        } catch (e: Exception) {
            DownloadProvider.BUILT_IN
        }
    }

    fun setDownloadProvider(provider: DownloadProvider) {
        prefs.edit().putString(KEY_DOWNLOAD_PROVIDER, provider.name).apply()
    }
}
