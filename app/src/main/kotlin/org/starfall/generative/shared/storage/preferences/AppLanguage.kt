package org.starfall.generative.shared.storage.preferences

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Languages offered in Preferences. A language without translated strings yet falls back to the
 * default (English) resources; add its strings to `values-<qualifier>/strings.xml` to translate it.
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    ENGLISH("en", "English"),
    VIETNAMESE("vi", "Tiếng Việt"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文"),
    CHINESE_TRADITIONAL("zh-TW", "繁體中文"),
    SPANISH("es", "Español");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: ENGLISH

        /** The supported language closest to [locale], or English when none matches. */
        fun closestTo(locale: Locale): AppLanguage {
            if (locale.language == "zh") {
                val traditional = locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")
                return if (traditional) CHINESE_TRADITIONAL else CHINESE_SIMPLIFIED
            }
            return entries.firstOrNull { it.locale.language == locale.language } ?: ENGLISH
        }
    }
}

/**
 * The language override has to be known before DataStore has loaded, when an Activity attaches its
 * base context, so it is mirrored into a small SharedPreferences file that can be read synchronously.
 */
object AppLocale {
    private const val FILE = "app_locale"
    private const val USE_SYSTEM = "use_system"
    private const val TAG = "tag"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Tag of the language chosen in Preferences, or null while the app follows the system language. */
    fun overrideTag(context: Context): String? {
        val prefs = prefs(context)
        return if (prefs.getBoolean(USE_SYSTEM, true)) null else AppLanguage.fromTag(prefs.getString(TAG, null)).tag
    }

    fun save(context: Context, useSystem: Boolean, tag: String) {
        prefs(context).edit().putBoolean(USE_SYSTEM, useSystem).putString(TAG, tag).apply()
    }

    /** Apply the chosen language to [base]; the system language is left untouched. */
    fun wrap(base: Context): Context {
        val tag = overrideTag(base)
        if (tag == null) {
            // Back to the system language: undo a Locale.setDefault from an earlier override.
            Locale.setDefault(base.resources.configuration.locales[0])
            return base
        }
        val locale = AppLanguage.fromTag(tag).locale
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
