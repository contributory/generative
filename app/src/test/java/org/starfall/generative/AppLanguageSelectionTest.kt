package org.starfall.generative

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.shared.storage.preferences.AppLanguage
import org.starfall.generative.shared.storage.preferences.AppLocale
import org.starfall.generative.shared.storage.preferences.AppPreferencesRepository

/** Language pick, resource selection and the locale override that Preferences drives. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppLanguageSelectionTest {
    private lateinit var context: Context
    private lateinit var defaultLocale: Locale

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        defaultLocale = Locale.US
        Locale.setDefault(defaultLocale)
        context.getSharedPreferences("app_locale", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        context.getSharedPreferences("app_locale", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Resources of [language] resolved through a configuration that carries its locale. */
    private fun localizedContext(language: AppLanguage): Context {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(language.locale)
        }
        return context.createConfigurationContext(configuration)
    }

    @Test
    fun everyLanguageResolvesToItsOwnEntry() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language, AppLanguage.fromTag(language.tag))
        }
    }

    @Test
    fun unknownTagsFallBackToEnglish() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("xx"))
    }

    @Test
    fun closestLanguageMatchesTheSystemLocale() {
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.closestTo(Locale.forLanguageTag("vi")))
        assertEquals(AppLanguage.FRENCH, AppLanguage.closestTo(Locale.forLanguageTag("fr-FR")))
        assertEquals(AppLanguage.GERMAN, AppLanguage.closestTo(Locale.forLanguageTag("de-AT")))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.closestTo(Locale.forLanguageTag("ja-JP")))
        assertEquals(AppLanguage.KOREAN, AppLanguage.closestTo(Locale.forLanguageTag("ko-KR")))
        assertEquals(AppLanguage.SPANISH, AppLanguage.closestTo(Locale.forLanguageTag("es-ES")))
        assertEquals(
            AppLanguage.PORTUGUESE_BRAZIL,
            AppLanguage.closestTo(Locale.forLanguageTag("pt-BR")),
        )
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.closestTo(Locale.forLanguageTag("ru-RU")))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.closestTo(Locale.forLanguageTag("it-IT")))
        assertEquals(AppLanguage.THAI, AppLanguage.closestTo(Locale.forLanguageTag("th-TH")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.closestTo(Locale.forLanguageTag("en-US")))
    }

    @Test
    fun closestLanguageHandlesBothIndonesianSpellings() {
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.closestTo(Locale.forLanguageTag("id")))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.closestTo(Locale.forLanguageTag("in")))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.closestTo(Locale("in")))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.closestTo(Locale("id")))
    }

    @Test
    fun closestLanguagePicksTheChineseVariant() {
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.closestTo(Locale.forLanguageTag("zh")))
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.closestTo(Locale.forLanguageTag("zh-CN")))
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.closestTo(Locale.forLanguageTag("zh-Hans")))
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.closestTo(Locale.forLanguageTag("zh-SG")))
        assertEquals(AppLanguage.CHINESE_TRADITIONAL, AppLanguage.closestTo(Locale.forLanguageTag("zh-TW")))
        assertEquals(AppLanguage.CHINESE_TRADITIONAL, AppLanguage.closestTo(Locale.forLanguageTag("zh-Hant")))
        assertEquals(AppLanguage.CHINESE_TRADITIONAL, AppLanguage.closestTo(Locale.forLanguageTag("zh-HK")))
        assertEquals(AppLanguage.CHINESE_TRADITIONAL, AppLanguage.closestTo(Locale.forLanguageTag("zh-MO")))
    }

    @Test
    fun unsupportedLanguagesFallBackToEnglish() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.closestTo(Locale.forLanguageTag("nl")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.closestTo(Locale.forLanguageTag("pl-PL")))
    }

    @Test
    fun resourceQualifiersPointAtTheTranslatedFolders() {
        assertEquals("zh-rCN", AppLanguage.CHINESE_SIMPLIFIED.resourceQualifier)
        assertEquals("zh-rTW", AppLanguage.CHINESE_TRADITIONAL.resourceQualifier)
        assertEquals("pt-rBR", AppLanguage.PORTUGUESE_BRAZIL.resourceQualifier)
        assertEquals("in", AppLanguage.INDONESIAN.resourceQualifier)
        assertEquals("it", AppLanguage.ITALIAN.resourceQualifier)
        assertEquals("th", AppLanguage.THAI.resourceQualifier)
        assertEquals("vi", AppLanguage.VIETNAMESE.resourceQualifier)
    }

    @Test
    fun everyLanguageShipsItsOwnTranslatedStrings() {
        val expected = mapOf(
            AppLanguage.ENGLISH to "Select a language model",
            AppLanguage.VIETNAMESE to "Chọn mô hình",
            AppLanguage.FRENCH to "Choisir un modèle",
            AppLanguage.GERMAN to "Modell auswählen",
            AppLanguage.JAPANESE to "モデルを選択",
            AppLanguage.KOREAN to "모델 선택",
            AppLanguage.CHINESE_SIMPLIFIED to "选择模型",
            AppLanguage.CHINESE_TRADITIONAL to "選擇模型",
            AppLanguage.SPANISH to "Elegir un modelo",
            AppLanguage.PORTUGUESE_BRAZIL to "Escolher um modelo",
            AppLanguage.RUSSIAN to "Выбрать модель",
            AppLanguage.ITALIAN to "Seleziona un modello di lingua",
            AppLanguage.THAI to "เลือกโมเดลภาษา",
            AppLanguage.INDONESIAN to "Pilih model bahasa",
        )
        assertEquals(AppLanguage.entries.toSet(), expected.keys.map { it.key }.toSet())

        // Indonesian lives in the legacy `values-in` folder, which the resource system matches by
        // qualifier instead of by the BCP-47 tag; it is covered by its own test below.
        expected
            .filterKeys { it != AppLanguage.INDONESIAN }
            .forEach { (language, text) ->
                assertEquals(language.tag, text, localizedContext(language).getString(R.string.select_model))
            }
    }

    @Test
    @Config(sdk = [28], qualifiers = "in")
    fun indonesianStringsResolveFromTheLegacyInQualifier() {
        assertEquals("Pilih model bahasa", context.getString(R.string.select_model))
        assertEquals("in", AppLanguage.INDONESIAN.resourceQualifier)
    }

    @Test
    fun languageNamesAreShownInTheAppLanguage() {
        val expected = mapOf(
            AppLanguage.ENGLISH to "Anh",
            AppLanguage.VIETNAMESE to "Tiếng Việt",
            AppLanguage.FRENCH to "Tiếng Pháp",
            AppLanguage.GERMAN to "Tiếng Đức",
            AppLanguage.JAPANESE to "Tiếng Nhật",
            AppLanguage.KOREAN to "Tiếng Hàn",
            AppLanguage.CHINESE_SIMPLIFIED to "Tiếng Trung Quốc (Giản thể)",
            AppLanguage.CHINESE_TRADITIONAL to "Tiếng Trung Quốc (Phồn thể)",
            AppLanguage.SPANISH to "Tiếng Tây Ban Nha",
            AppLanguage.PORTUGUESE_BRAZIL to "Tiếng Bồ Đào Nha (Brazil)",
            AppLanguage.RUSSIAN to "Tiếng Nga",
            AppLanguage.ITALIAN to "Tiếng Ý",
            AppLanguage.THAI to "Tiếng Thái",
            AppLanguage.INDONESIAN to "Tiếng Indonesia",
        )
        assertEquals(AppLanguage.entries.toSet(), expected.keys.map { it.key }.toSet())

        val localized = localizedContext(AppLanguage.VIETNAMESE)
        expected.forEach { (language, text) ->
            assertEquals(language.tag, text, localized.getString(language.nameRes))
        }
    }

    @Test
    @Config(sdk = [28], qualifiers = "in")
    fun indonesianLanguageNamesResolveFromTheLegacyInQualifier() {
        assertEquals("Italia", context.getString(AppLanguage.ITALIAN.nameRes))
        assertEquals("Indonesia", context.getString(AppLanguage.INDONESIAN.nameRes))
    }

    @Test
    fun everyLocaleProvidesTheProfileStepsPlural() {
        AppLanguage.entries
            .filter { it != AppLanguage.INDONESIAN }
            .forEach { language ->
                val resources = localizedContext(language).resources
                val one = resources.getQuantityString(R.plurals.media_chain_profile_steps, 1, 1)
                val other = resources.getQuantityString(R.plurals.media_chain_profile_steps, 3, 3)
                assertTrue(language.tag, one.isNotBlank())
                assertTrue(language.tag, other.isNotBlank())
                assertTrue(language.tag, one.contains("1"))
                assertTrue(language.tag, other.contains("3"))
            }
    }

    @Test
    @Config(sdk = [28], qualifiers = "in")
    fun indonesianProvidesTheProfileStepsPlural() {
        val steps = context.resources.getQuantityString(R.plurals.media_chain_profile_steps, 3, 3)
        assertTrue(steps.contains("3"))
    }

    @Test
    fun savedLanguageOverrideDrivesTheWrappedContext() {
        AppLocale.save(context, useSystem = false, tag = AppLanguage.ITALIAN.tag)
        assertEquals(AppLanguage.ITALIAN.tag, AppLocale.overrideTag(context))

        val wrapped = AppLocale.wrap(context)
        assertEquals(AppLanguage.ITALIAN.tag, wrapped.resources.configuration.locales[0].toLanguageTag())
        assertEquals(AppLanguage.ITALIAN.locale, Locale.getDefault())
        assertEquals("Seleziona un modello di lingua", wrapped.getString(R.string.select_model))
    }

    @Test
    fun followingTheSystemLanguageKeepsTheBaseContext() {
        AppLocale.save(context, useSystem = true, tag = AppLanguage.THAI.tag)
        assertNull(AppLocale.overrideTag(context))

        val wrapped = AppLocale.wrap(context)
        assertSame(context, wrapped)
        assertNotEquals(AppLanguage.THAI.locale, Locale.getDefault())
    }

    @Test
    fun preferencesMirrorTheLanguageChoiceIntoTheOverride() = runBlocking {
        val repository = AppPreferencesRepository(context)

        repository.setAppLanguage(useSystem = false, tag = AppLanguage.THAI.tag)
        assertEquals(AppLanguage.THAI.tag, AppLocale.overrideTag(context))
        assertEquals(
            "เลือกโมเดลภาษา",
            AppLocale.wrap(context).getString(R.string.select_model),
        )

        repository.setAppLanguage(useSystem = true, tag = AppLanguage.THAI.tag)
        assertNull(AppLocale.overrideTag(context))
    }
}
