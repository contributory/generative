# Full App Localization — Complete Existing Languages + Italian, Thai, Indonesian

## Goal

Make every supported UI language complete and natural, using `app/src/main/res/values/strings.xml` (English) as the **canonical meaning and key inventory**. Add Italian, Thai, and Indonesian to the app's language selector, and finish/quality-check **every existing translation**. Preserve technical accuracy and keep translations compact enough for the existing layouts. English fallback is a safety net, **not** a substitute for completion.

**Status:** Planned — implementation and translations have not started. Work in the order below; check items only when implemented and verified. Do not combine this project with unrelated UI refactors.

## Baseline (2026-10-27)

- English `values/strings.xml`: **1,187 `<string>` entries**: **1,042 translatable** and **145 marked `translatable="false"`**, plus **one `<plurals>` resource** (`media_chain_profile_steps`). Audit the non-translatable flags: some currently hide user-facing text from localization; leave truly invariant values alone, but do not assume every flag is intentional.
- Existing translated resource folders: `values-de`, `values-es`, `values-fr`, `values-ja`, `values-ko`, `values-pt-rBR`, `values-ru`, `values-vi`, `values-zh-rCN`, `values-zh-rTW`. Most have **167** string keys (Vietnamese: **171**), leaving **875** English translatable string keys absent (Vietnamese: **871**). None currently translates the plurals resource.
- `AppLanguage` in `shared/storage/preferences/AppLanguage.kt` drives the Settings dropdown, saved locale override, and system-locale matching. **Russian resources exist but Russian is missing from the enum**, so include it in the selectable-language audit.
- `scripts/translate_strings.py` currently skips short strings by default (`--min-words 3`), never revises existing translations, and skips some structured content. **It cannot achieve or certify full coverage as-is.** Any automated draft requires terminology review, placeholder validation, and UI checks.

## Language inventory

| Language | App locale tag | Android resources | Work |
|---|---|---|---|
| English (source) | `en` | `values/` | Canonical source; do not translate |
| Vietnamese | `vi` | `values-vi/` | Complete + revise existing |
| French | `fr` | `values-fr/` | Complete + revise existing |
| German | `de` | `values-de/` | Complete + revise existing |
| Japanese | `ja` | `values-ja/` | Complete + revise existing |
| Korean | `ko` | `values-ko/` | Complete + revise existing |
| Chinese (Simplified) | `zh-CN` | `values-zh-rCN/` | Complete + revise existing |
| Chinese (Traditional) | `zh-TW` | `values-zh-rTW/` | Complete + revise existing |
| Spanish | `es` | `values-es/` | Complete + revise existing |
| Portuguese (Brazil) | `pt-BR` | `values-pt-rBR/` | Complete + revise existing |
| Russian | `ru` | `values-ru/` | Complete + revise existing; expose in picker |
| **Italian** | **`it`** | **`values-it/`** | **New language + full translations** |
| **Thai** | **`th`** | **`values-th/`** | **New language + full translations** |
| **Indonesian** | **`id`** | **`values-in/`** | **New language + full translations**; use Android's established `in` resource qualifier for Indonesian, verify packaging and selection via `id` locale tag |

Target: **13 translated languages + English**, with full coverage of all translatable source keys and plural quantities in each locale.

## Translation rules (apply to every language and every key)

1. **Translate from the English original, by key and UI context.** Preserve meaning, action, tone, polarity, and whether text is a label, button, error, hint, accessibility description, or confirmation. Never use another translation as the source of truth. Review the old 167/171 translations too; do not blindly preserve or overwrite them.
2. **Keep technical terms in English when the local equivalent is unknown, nonstandard, ambiguous, or misleading.** Prefer terminology users of Android/developer/AI tools actually recognize. Examples that may remain English depending on context: API, SDK, JSON, SSH, SFTP, OAuth, MCP, Tool, Prompt, Token, Provider, Context, Terminal, Model, Profile. These are **examples, not a mandatory untranslated list**: translate a term when the local equivalent is accurate, familiar, and shorter/clearer. Use each term consistently within that locale.
3. **Avoid misleading literal translations.** For example, an AI *Model*, a developer *Tool*, and a configuration *Profile* must not be translated into words that suggest a human model, physical tool, or social-media profile. If the meaning would be unclear, retain the English term rather than inventing one.
4. **Aim for English-like display length, especially for compact UI.** Prefer concise native wording, not word-for-word translation. Avoid adding explanations to buttons, menu items, tab labels, chips, and AppBars. Use a reasonable *rendered-width* budget; character counts alone are not comparable across Japanese, Chinese, Thai, and Latin scripts. Do not shorten so aggressively that meaning is lost; test real layouts instead.
5. **Keep data syntax unchanged.** Preserve `%s`, `%d`, `%1$s`, `%2$d`, `%%`, positional argument indices/types, line breaks (`\\n`), escaped symbols, markup/spans, URLs, Markdown/code fences, sample commands, environment variables (`$HOME`), product names, API identifiers, file extensions, and paths as applicable. Translate only the human-readable parts. Maintain valid Android XML quoting, apostrophe escaping, and `@`/`?` handling.
6. **Respect context and grammar.** Keep natural capitalization, punctuation, respectful tone, plural/gender requirements and word order for each language. Do not translate brand names or technical acronyms unpredictably. Handle whitespace/formatting intentionally.
7. **Do not hide gaps behind `translatable="false"`.** Keep those flags only for genuinely invariant, brand, format, or machine-readable text. Audit user-facing strings currently excluded (notably the Remote Machines and terminal sections); explicitly decide which must become translatable and then include them in every language.

## Implementation checklist

### Phase 1 — Inventory and terminology

- [ ] Reconcile current English keys with all locale files, including newly added source strings; inventory duplicates, obsolete keys, intentionally non-translatable entries, placeholders, escapes, `plurals`, hard-coded UI strings, and context-dependent labels.
- [ ] Classify existing `translatable="false"` strings: invariant/technical vs. actually user-facing and needing localization. Document intentional exceptions; translate newly eligible keys in every locale.
- [ ] Establish a brief English→locale glossary for recurring product and technical terms and disambiguate `Model`, `Provider`, `Tool`, `Skill`, `Profile`, `Prompt`, `Context`, `Stream`, `Terminal`, `Storage`, `Agent`, etc. Review the glossary per language, not as a universal fixed translation map.
- [ ] Define a reproducible coverage report comparing each locale to the English source, including plural quantities, without incorrectly counting invariant resources as missing.

### Phase 2 — Language support

- [ ] Add `ITALIAN("it", "Italiano")`, `THAI("th", "ไทย")`, and `INDONESIAN("id", "Bahasa Indonesia")` to `AppLanguage` and create `values-it/strings.xml`, `values-th/strings.xml`, and `values-in/strings.xml`.
- [ ] Add `RUSSIAN("ru", "Русский")` to `AppLanguage` to expose the already-existing Russian resource folder; preserve existing user preferences and stable tags.
- [ ] Verify picker entries, `AppLanguage.fromTag` / `closestTo`, system-language following, saved override, Activity recreation, and Indonesian `id`/`in` resolution on supported Android versions.

### Phase 3 — Full translations

- [ ] Translate **all** English translatable string keys in **Italian**; add all plural forms required by the locale.
- [ ] Translate **all** English translatable string keys in **Thai**; add all plural forms required by the locale.
- [ ] Translate **all** English translatable string keys in **Indonesian**; add all plural forms required by the locale.
- [ ] Complete missing and review existing entries for **Vietnamese, French, German, Japanese, and Korean**.
- [ ] Complete missing and review existing entries for **Simplified Chinese, Traditional Chinese, Spanish, Brazilian Portuguese, and Russian**.
- [ ] Normalize shared technical terms and shorten overly long labels across **every** target language. Preserve English terms wherever localization is confusing or inaccurate.
- [ ] Translate plural items correctly (`one`, `other`, and any Android/CLDR categories the target locale needs); do not copy English plural grammar uncritically.

### Phase 4 — Automated checks and QA

- [ ] Improve or supplement `scripts/translate_strings.py` with a **local, offline coverage/validation command** that checks all keys (including one-word labels), duplicate keys, plurals, placeholder identity/index/type, invalid XML and Android resource escapes; it must never certify coverage merely because a locale falls back to English.
- [ ] Validate each locale against the English key inventory: **zero missing required keys**, **zero unexpected obsolete keys** (unless explicitly documented), **zero broken format arguments/XML**. Mark intentional English technical terms explicitly as reviewed, not missing.
- [ ] Build resources / run applicable unit tests and lint **once** after changes; add targeted tests for locale selection and format/plural safety. Follow `AGENTS.md` and avoid duplicate concurrent Gradle processes.
- [ ] On device/emulator, review narrow phones and large font scale in Settings, Provider/Model editors, Tool/Skill screens, Chat, Media Preview, Remote Machines, menus, dialogs, bottom sheets, notifications, and accessibility labels. Check truncation, clipping, Thai wrapping, CJK text, and RTL neutrality where relevant.
- [ ] Switch each supported language explicitly and by system locale; verify no unexpected English fallback, broken text, or stale locale after relaunch. Re-run coverage after English strings change during implementation.
- [ ] Record final per-language coverage and verification results here; only then mark the plan complete.

## Acceptance criteria

- App lists Italian, Thai, Indonesian **and all existing supported resource languages** (including Russian); language choice persists and system matching works.
- Every locale contains every eligible English key and translates plural resources as needed, except a clearly documented set of intentionally invariant/English technical terms that are **explicitly present and reviewed**.
- All translations are semantically correct, concise for the UI, consistent in terminology, and keep genuinely ambiguous/unknown technical vocabulary in English.
- No formatting placeholder, XML resource, interaction, accessibility label, or critical UI layout regresses; coverage and platform checks are recorded rather than assumed.
