package io.github.gracethings.appoops.data.preference

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import io.github.gracethings.appoops.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    SYSTEM("", R.string.setting_language_system),
    ENGLISH("en", R.string.setting_language_en),
    CHINESE_CN("zh-CN", R.string.setting_language_zh_cn),
    CHINESE_HK("zh-HK", R.string.setting_language_zh_hk),
    CHINESE_TW("zh-TW", R.string.setting_language_zh_tw),
    JAPANESE("ja", R.string.setting_language_ja),
    KOREAN("ko", R.string.setting_language_ko),
    SPANISH("es", R.string.setting_language_es),
    FRENCH("fr", R.string.setting_language_fr),
    GERMAN("de", R.string.setting_language_de),
    ITALIAN("it", R.string.setting_language_it);

    companion object {
        fun fromTag(tag: String): AppLanguage {
            val normalized = tag.trim().lowercase(Locale.ROOT).replace('_', '-')
            return when {
                normalized.isEmpty() -> SYSTEM
                normalized.startsWith("zh") -> when {
                    normalized.contains("-hk") || normalized.contains("-mo") -> CHINESE_HK
                    normalized.contains("-tw") || normalized.contains("hant") -> CHINESE_TW
                    else -> CHINESE_CN
                }
                normalized.startsWith("ja") -> JAPANESE
                normalized.startsWith("ko") -> KOREAN
                normalized.startsWith("es") -> SPANISH
                normalized.startsWith("fr") -> FRENCH
                normalized.startsWith("de") -> GERMAN
                normalized.startsWith("it") -> ITALIAN
                normalized.startsWith("en") -> ENGLISH
                else -> SYSTEM
            }
        }
    }
}

object LanguagePreferences {
    private const val PREFS_NAME = "app_oops_language_prefs"
    private const val KEY_LANGUAGE = "app_language"

    private val _language = MutableStateFlow(AppLanguage.SYSTEM)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun init(context: Context) {
        val saved = readSavedLanguage(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = context.getSystemService(LocaleManager::class.java)
            val appLocales = lm?.applicationLocales
            if (appLocales != null && !appLocales.isEmpty) {
                val synced = AppLanguage.fromTag(appLocales.toLanguageTags())
                _language.value = synced
                persistLanguage(context, synced)
                return
            } else if (saved != AppLanguage.SYSTEM) {
                lm?.applicationLocales = LocaleList.forLanguageTags(saved.tag)
            }
        }
        _language.value = saved
    }

    fun syncFromSystem(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = context.getSystemService(LocaleManager::class.java) ?: return
            val appLocales = lm.applicationLocales
            val actual = if (appLocales.isEmpty) {
                AppLanguage.SYSTEM
            } else {
                AppLanguage.fromTag(appLocales.toLanguageTags())
            }
            if (_language.value != actual) {
                _language.value = actual
                persistLanguage(context, actual)
            }
        }
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        val changed = _language.value != language
        _language.value = language
        persistLanguage(context, language)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = context.getSystemService(LocaleManager::class.java)
            val targetLocales = if (language == AppLanguage.SYSTEM) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(language.tag)
            }
            if (lm != null && lm.applicationLocales != targetLocales) {
                lm.applicationLocales = targetLocales
            }
        } else if (changed) {
            applyLegacyResources(context.applicationContext, language)
            context.findActivity()?.recreate()
        }
    }

    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }
        val lang = readSavedLanguage(base)
        if (lang == AppLanguage.SYSTEM) {
            return base
        }
        val locale = Locale.forLanguageTag(lang.tag)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    private fun readSavedLanguage(context: Context): AppLanguage {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.name) ?: AppLanguage.SYSTEM.name
        if (raw == "CHINESE") return AppLanguage.CHINESE_CN
        return runCatching { AppLanguage.valueOf(raw) }.getOrDefault(AppLanguage.SYSTEM)
    }

    private fun persistLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LANGUAGE, language.name)
            .apply()
    }

    @Suppress("DEPRECATION")
    private fun applyLegacyResources(appContext: Context, language: AppLanguage) {
        val resources = appContext.resources
        val config = Configuration(resources.configuration)
        val locales = if (language == AppLanguage.SYSTEM) {
            LocaleList.getAdjustedDefault()
        } else {
            LocaleList(Locale.forLanguageTag(language.tag))
        }
        config.setLocales(locales)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
