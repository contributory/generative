package org.starfall.generative.shared.storage.preferences

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import org.starfall.generative.R

/**
 * Languages offered in Preferences. Every language listed here ships a translated
 * `values-<resourceQualifier>/strings.xml`; a language without translated strings falls back to the
 * default (English) resources.
 *
 * [nativeName] is the name of the language in the language itself and never changes; [nameRes] is
 * the name of the language in the app language, so the picker can show both.
 */
enum class AppLanguage(val tag: String, val nativeName: String, val nameRes: Int) {
    ENGLISH("en", "English", R.string.language_name_en),
    VIETNAMESE("vi", "Tiếng Việt", R.string.language_name_vi),
    FRENCH("fr", "Français", R.string.language_name_fr),
    GERMAN("de", "Deutsch", R.string.language_name_de),
    JAPANESE("ja", "日本語", R.string.language_name_ja),
    KOREAN("ko", "한국어", R.string.language_name_ko),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文", R.string.language_name_zh_cn),
    CHINESE_TRADITIONAL("zh-TW", "繁體中文", R.string.language_name_zh_tw),
    SPANISH("es", "Español", R.string.language_name_es),
    PORTUGUESE_BRAZIL("pt-BR", "Português (Brasil)", R.string.language_name_pt_br),
    RUSSIAN("ru", "Русский", R.string.language_name_ru),
    ITALIAN("it", "Italiano", R.string.language_name_it),
    THAI("th", "ไทย", R.string.language_name_th),
    INDONESIAN("id", "Bahasa Indonesia", R.string.language_name_id);

    val locale: Locale get() = Locale.forLanguageTag(tag)

    /**
     * Android resource folder qualifier holding this language's strings, for example `values-pt-rBR`.
     * Indonesian keeps the legacy `in` qualifier even though its BCP-47 tag is `id`.
     */
    val resourceQualifier: String
        get() = when (this) {
            CHINESE_SIMPLIFIED -> "zh-rCN"
            CHINESE_TRADITIONAL -> "zh-rTW"
            PORTUGUESE_BRAZIL -> "pt-rBR"
            INDONESIAN -> "in"
            else -> tag
        }

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: ENGLISH

        /** The supported language closest to [locale], or English when none matches. */
        fun closestTo(locale: Locale): AppLanguage {
            if (locale.language == "zh") {
                val traditional = locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")
                return if (traditional) CHINESE_TRADITIONAL else CHINESE_SIMPLIFIED
            }
            val language = normalizeLanguage(locale.language)
            return entries.firstOrNull { normalizeLanguage(it.locale.language) == language } ?: ENGLISH
        }

        /**
         * Legacy language code of a locale. Android names Indonesian `in` in resources and `id` in
         * newer platform releases, so both spellings have to match the same entry.
         */
        private fun normalizeLanguage(language: String): String = when (language) {
            "in", "id" -> "in"
            else -> language
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
