# Full App Localization — Complete Existing Languages + Italian, Thai, Indonesian

## Goal

Make every supported UI language complete and natural, using `app/src/main/res/values/strings.xml` (English) as the **canonical meaning and key inventory**. Add Italian, Thai, and Indonesian to the app's language selector, and finish/quality-check **every existing translation**. Preserve technical accuracy and keep translations compact enough for the existing layouts. English fallback is a safety net, **not** a substitute for completion.

**Status:** Implemented (2026-10-27) — all 13 translated languages ship 1,201/1,201 required strings plus the
`media_chain_profile_steps` plural, with 0 errors from the offline checker. **Not run:** the Gradle build, unit
tests, lint and the on-device/emulator UI pass — this sandbox has no JDK, no Android SDK and no cached Gradle
distribution (see [Verification record](#verification-record)). Do not combine this project with unrelated UI refactors.

## Baseline (2026-10-27)

_This is the state before the change. The audit outcome and the per-language result are in the
[Verification record](#verification-record)._

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

- [x] Reconcile current English keys with all locale files, including newly added source strings; inventory duplicates, obsolete keys, intentionally non-translatable entries, placeholders, escapes, `plurals`, hard-coded UI strings, and context-dependent labels.
- [x] Classify existing `translatable="false"` strings: invariant/technical vs. actually user-facing and needing localization. Document intentional exceptions; translate newly eligible keys in every locale.
- [x] Establish a brief English→locale glossary for recurring product and technical terms and disambiguate `Model`, `Provider`, `Tool`, `Skill`, `Profile`, `Prompt`, `Context`, `Stream`, `Terminal`, `Storage`, `Agent`, etc. Review the glossary per language, not as a universal fixed translation map.
- [x] Define a reproducible coverage report comparing each locale to the English source, including plural quantities, without incorrectly counting invariant resources as missing.

### Phase 2 — Language support

- [x] Add `ITALIAN("it", "Italiano")`, `THAI("th", "ไทย")`, and `INDONESIAN("id", "Bahasa Indonesia")` to `AppLanguage` and create `values-it/strings.xml`, `values-th/strings.xml`, and `values-in/strings.xml`.
- [x] Add `RUSSIAN("ru", "Русский")` to `AppLanguage` to expose the already-existing Russian resource folder; preserve existing user preferences and stable tags.
- [x] Verify picker entries, `AppLanguage.fromTag` / `closestTo`, system-language following, saved override, Activity recreation, and Indonesian `id`/`in` resolution on supported Android versions.

### Phase 3 — Full translations

- [x] Translate **all** English translatable string keys in **Italian**; add all plural forms required by the locale.
- [x] Translate **all** English translatable string keys in **Thai**; add all plural forms required by the locale.
- [x] Translate **all** English translatable string keys in **Indonesian**; add all plural forms required by the locale.
- [x] Complete missing and review existing entries for **Vietnamese, French, German, Japanese, and Korean**.
- [x] Complete missing and review existing entries for **Simplified Chinese, Traditional Chinese, Spanish, Brazilian Portuguese, and Russian**.
- [x] Normalize shared technical terms and shorten overly long labels across **every** target language. Preserve English terms wherever localization is confusing or inaccurate.
- [x] Translate plural items correctly (`one`, `other`, and any Android/CLDR categories the target locale needs); do not copy English plural grammar uncritically.

### Phase 4 — Automated checks and QA

- [x] Improve or supplement `scripts/translate_strings.py` with a **local, offline coverage/validation command** that checks all keys (including one-word labels), duplicate keys, plurals, placeholder identity/index/type, invalid XML and Android resource escapes; it must never certify coverage merely because a locale falls back to English.
- [x] Validate each locale against the English key inventory: **zero missing required keys**, **zero unexpected obsolete keys** (unless explicitly documented), **zero broken format arguments/XML**. Mark intentional English technical terms explicitly as reviewed, not missing.
- [ ] Build resources / run applicable unit tests and lint **once** after changes; add targeted tests for locale selection and format/plural safety.
  - Targeted tests **were added** (`app/src/test/java/org/starfall/generative/AppLanguageSelectionTest.kt`, 12 cases covering `fromTag`, `closestTo`, resource qualifiers, per-language resource resolution, plural safety and the `AppLocale`/`AppPreferencesRepository` override).
  - The Gradle run itself is **not run** here: this sandbox has no JDK, no Android SDK and no cached Gradle distribution, so `assembleDebug`, `lintDebug` and `testDebugUnitTest` could not be executed. Run them once in a full toolchain environment (see [Verification record](#verification-record)).
- [ ] **Not run** — on device/emulator, review narrow phones and large font scale in Settings, Provider/Model editors, Tool/Skill screens, Chat, Media Preview, Remote Machines, menus, dialogs, bottom sheets, notifications, and accessibility labels. Check truncation, clipping, Thai wrapping, CJK text, and RTL neutrality where relevant.
- [ ] **Not run** — switch each supported language explicitly and by system locale; verify no unexpected English fallback, broken text, or stale locale after relaunch. Re-run coverage after English strings change during implementation.
- [x] Record final per-language coverage and verification results here; only then mark the plan complete.

## Acceptance criteria

- App lists Italian, Thai, Indonesian **and all existing supported resource languages** (including Russian); language choice persists and system matching works.
- Every locale contains every eligible English key and translates plural resources as needed, except a clearly documented set of intentionally invariant/English technical terms that are **explicitly present and reviewed**.
- All translations are semantically correct, concise for the UI, consistent in terminology, and keep genuinely ambiguous/unknown technical vocabulary in English.
- No formatting placeholder, XML resource, interaction, accessibility label, or critical UI layout regresses; coverage and platform checks are recorded rather than assumed.

## Glossary per locale

Recurring product and technical terms, as they are actually used in the shipped strings. Terms listed as
“kept in English” were reviewed and deliberately left untranslated because a local equivalent would be
ambiguous, non-standard or misleading (API names, wire formats, developer vocabulary). The list is a guide
for future edits, not a fixed translation table — keep it consistent per locale, not across locales.

| English | vi | fr | de | ja | ko |
|---|---|---|---|---|---|
| model | Mô hình | Modèle | Modell | モデル | 모델 |
| model provider | Nhà cung cấp mô hình | Fournisseur de modèles | Modellanbieter | モデルプロバイダー | 모델 제공업체 |
| tool | Công cụ | Outil | Tool | ツール | 도구 |
| prompt | Prompt | Prompt | Prompt | プロンプト | 프롬프트 |
| token | Token | Token | Token | トークン | 토큰 |
| context window | Cửa sổ ngữ cảnh | Fenêtre de contexte | Kontextfenster | コンテキストウィンドウ | 컨텍스트 창 |
| terminal | Terminal | Terminal | Terminal | ターミナル | 터미널 |
| profile | Hồ sơ | Profil | Profil | プロファイル | 프로필 |
| skill | Kỹ năng | Compétence | Skill | スキル | 스킬 |
| conversation | Cuộc trò chuyện | Conversation | Unterhaltung | 会話 | 대화 |
| chat | Chat | Discussion | Chat | チャット | 채팅 |
| instruction | Chỉ dẫn | Instruction | Anweisung | 指示 | 지침 |
| folder | Thư mục | Dossier | Ordner | フォルダー | 폴더 |
| settings | Cài đặt | Paramètres | Einstellungen | 設定 | 설정 |
| speech service | Dịch vụ giọng nói | Service vocal | Sprachdienst | 音声サービス | 음성 서비스 |
| voice | Giọng nói | Voix | Stimme | 音声 | 음성 |
| reasoning | Suy luận | Raisonnement | Denken | 推論 | 추론 |
| summary | Tóm tắt | Résumé | Zusammenfassung | 要約 | 요약 |
| storage | Bộ nhớ | Stockage | Speicher | ストレージ | 저장소 |
| media | Media | Médias | Medien | メディア | 미디어 |
| agent | Agent | Agent | Agent | エージェント | 에이전트 |
| capabilities | Khả năng | Capacités | Fähigkeiten | 機能 | 기능 |
| headers | Header | En-têtes | Header | ヘッダー | 헤더 |
| endpoint | Endpoint | Endpoint | Endpunkt | エンドポイント | 엔드포인트 |
| aspect ratio | Tỷ lệ khung hình | Rapport d'aspect | Seitenverhältnis | アスペクト比 | 종횡비 |
| safety filter | Bộ lọc an toàn | Filtre de sécurité | Sicherheitsfilter | セーフティフィルター | 안전 필터 |
| negative prompt | Prompt phủ định | Prompt négatif | Negativer Prompt | ネガティブプロンプ트 | 네거티브 프롬프트 |
| temperature | Nhiệt độ | Température | Temperatur | 温度 | 온도 |
| guidance scale | Thang hướng dẫn | Échelle de guidance | Guidance-Skala | ガイダンススケール | 가이던스 스케일 |
| kept in English | API, JSON, SSH, SFTP, OAuth, MCP, Seed, Top-p, Top-k, IdentityFile | API, JSON, SSH, SFTP, OAuth, MCP, Seed, Top-p, Top-k, IdentityFile | API, JSON, SSH, SFTP, OAuth, MCP, Seed, Top-p, Top-k, IdentityFile | API, JSON, SSH, SFTP, OAuth, MCP, Seed, Top-p, Top-k, IdentityFile | API, JSON, SSH, SFTP, OAuth, MCP, Seed, Top-p, Top-k, IdentityFile |

| English | zh-CN | zh-TW | es | pt-BR | ru |
|---|---|---|---|---|---|
| model | 模型 | 模型 | Modelo | Modelo | Модель |
| model provider | 模型提供商 | 模型供應商 | Proveedor de modelos | Provedor de modelos | Провайдер моделей |
| tool | 工具 | 工具 | Herramienta | Ferramenta | Инструмент |
| prompt | 提示词 | 提示詞 | Prompt | Prompt | Промпт |
| token | Token | Token | Token | Token | Токен |
| context window | 上下文窗口 | 上下文視窗 | Ventana de contexto | Janela de contexto | Контекстное окно |
| terminal | 终端 | 終端機 | Terminal | Terminal | Терминал |
| profile | 配置 | 設定檔 | Perfil | Perfil | Профиль |
| skill | 技能 | 技能 | Habilidad | Habilidade | Навык |
| conversation | 对话 | 對話 | Conversación | Conversa | Беседа |
| chat | 聊天 | 聊天 | Chat | Chat | Чат |
| instruction | 指令 | 指令 | Instrucción | Instrução | Инструкция |
| folder | 文件夹 | 資料夾 | Carpeta | Pasta | Папка |
| settings | 设置 | 設定 | Ajustes | Configurações | Настройки |
| speech service | 语音服务 | 語音服務 | Servicio de voz | Serviço de voz | Голосовой сервис |
| voice | 语音 | 語音 | Voz | Voz | Голос |
| reasoning | 推理 | 推理 | Razonamiento | Raciocínio | Рассуждение |
| summary | 摘要 | 摘要 | Resumen | Resumo | Резюме |
| storage | 存储 | 儲存空間 | Almacenamiento | Armazenamento | Хранилище |
| media | 媒体 | 媒體 | Multimedia | Mídia | Медиа |
| agent | 智能体 | 代理 | Agente | Agente | Агент |
| capabilities | 能力 | 能力 | Capacidades | Capacidades | Возможности |
| headers | 请求头 | 標頭 | Cabeceras | Cabeçalhos | Заголовки |
| endpoint | 端点 | 端點 | Endpoint | Endpoint | Конечная точка |
| aspect ratio | 宽高比 | 長寬比 | Relación de aspecto | Proporção da imagem | Соотношение сторон |
| safety filter | 安全过滤器 | 安全篩選器 | Filtro de seguridad | Filtro de segurança | Фильтр безопасности |
| negative prompt | 反向提示词 | 負向提示詞 | Prompt negativo | Prompt negativo | Негативный промпт |
| temperature | 温度 | 溫度 | Temperatura | Temperatura | Температура |
| guidance scale | 引导强度 | 引導強度 | Escala de guía | Escala de orientação | Шкала руководства |
| kept in English | API, JSON, SSH, SFTP, OAuth, MCP, Seed, Top-p, Top-k, IdentityFile | API, JSON, SSH, SFTP, OAuth, MCP, Seed, Top-p, Top-k, IdentityFile | API, JSON, SSH, SFTP, OAuth, MCP, Seed, Top-p, Top-k, IdentityFile | API, JSON, SSH, SFTP, OAuth, MCP, Seed, Top-p, Top-k, IdentityFile | API, JSON, SSH, SFTP, OAuth, MCP, Seed, Top-p, Top-k, IdentityFile |

| English | it | th | id |
|---|---|---|---|
| model | Modello | โมเดล | Model |
| model provider | Fornitore di modelli | ผู้ให้บริการโมเดล | Penyedia model |
| tool | Strumento | เครื่องมือ | Alat |
| prompt | Prompt | พรอมต์ | Prompt |
| token | Token | โทเคน | Token |
| context window | Finestra di contesto | หน้าต่างบริบท | Jendela konteks |
| terminal | Terminal | เทอร์มินัล | Terminal |
| profile | Profil | โปรไฟล์ | Profil |
| skill | Skill | สกิล | Skill |
| conversation | Conversazione | การสนทนา | Percakapan |
| chat | Chat | แชท | Obrolan |
| instruction | Istruzione | คำสั่ง | Instruksi |
| folder | Cartella | โฟลเดอร์ | Folder |
| settings | Impostazioni | การตั้งค่า | Pengaturan |
| speech service | Servizio vocale | บริการเสียงพูด | Layanan suara |
| voice | Voce | เสียง | Suara |
| reasoning | Ragionamento | การคิด | Penalaran |
| summary | Riepilogo | สรุป | Ringkasan |
| storage | Archiviazione | ที่เก็บข้อมูล | Penyimpanan |
| media | Media | สื่อ | Media |
| agent | Agente | เอเจนต์ | Agen |
| capabilities | Capacità | ความสามารถ | Kemampuan |
| headers | Intestazioni | ส่วนหัว | Header |
| endpoint | Endpoint | จุดปลายทาง | Endpoint |
| aspect ratio | Proporzioni | อัตราส่วนภาพ | Rasio aspek |
| safety filter | Filtro di sicurezza | ตัวกรองความปลอดภัย | Filter keamanan |
| negative prompt | Prompt negativo | พรอมต์เชิงลบ | Prompt negatif |
| temperature | Temperatura | อุณหภูมิ | Suhu |
| guidance scale | Scala di guida | ระดับการกำกับดูแล | Skala panduan |
| kept in English | API, JSON, SSH, SFTP, OAuth, MCP, Seed, Top-p, Top-k, IdentityFile | API, JSON, SSH, SFTP, OAuth, MCP, Seed, Top-p, Top-k, IdentityFile | API, JSON, SSH, SFTP, OAuth, MCP, Seed, Top-p, Top-k, IdentityFile |

### Deliberately invariant (not localized)

| Key | Reason |
|---|---|
| `remote_machine_sftp_root_summary` | Pure machine-readable format `"%1$s@%2$s · %3$s"` (user@host · path). Kept `translatable="false"` in the English source and therefore absent from every locale file, where Android falls back to the default value. |
| `settings_license_name` | Product/license name (`Starfall Org Contributor Commercial Copyleft License v1.1`). |
| `duration_hours_minutes_seconds` | Compact duration tokens (`%1$dh %2$dm %3$ds`) that read identically in every locale. |
| `chat_context_window_tokens` | Numeric token counter (`%1$s / %2$s tokens`) — locale-neutral units. |
| `remote_machine_google_section`, `remote_machine_update_ip_title` | Product names (`Google Cloud OAuth`, `Cloud Shell IP · %1$s`). |
| `settings_version`, `image_option_style_dall_e_3` | Version/product labels (`Version %1$s (%2$d)`, `Style (DALL·E 3)`). |

These are **present and reviewed**, not missing: the checker reports them as `warning untranslated: identical to the
English source` instead of as missing coverage.

## Language picker names

The language picker shows every language twice: the name written in the **app language** (larger) and the
**native name** of that language (smaller, below it). The native name never changes, so it stays in
`AppLanguage.nativeName`; the app-language name is a resource (`language_name_<tag>`) that every locale
translates, so a Vietnamese speaker reads *Tiếng Ý · Italiano* while a German speaker reads *Italienisch · Italiano*.

| Key | Language | English source |
|---|---|---|
| `language_name_en` | English | English |
| `language_name_vi` | Vietnamese | Vietnamese |
| `language_name_fr` | French | French |
| `language_name_de` | German | German |
| `language_name_ja` | Japanese | Japanese |
| `language_name_ko` | Korean | Korean |
| `language_name_zh_cn` | Chinese (Simplified) | Chinese (Simplified) |
| `language_name_zh_tw` | Chinese (Traditional) | Chinese (Traditional) |
| `language_name_es` | Spanish | Spanish |
| `language_name_pt_br` | Portuguese (Brazil) | Portuguese (Brazil) |
| `language_name_ru` | Russian | Russian |
| `language_name_it` | Italian | Italian |
| `language_name_th` | Thai | Thai |
| `language_name_id` | Indonesian | Indonesian |

## Review follow-up (2026-10-28)

The pull request review raised two findings on the localization work; both are fixed on the same branch.

### P0 — `aapt2` refused the resource set (blocking)

`remote_machine_websocket_url_help` carries the literal substitutions `%h` and `%p` without `formatted="false"`.
`aapt2 compile` rejects a translatable string that mixes several substitutions in a non-positional format
(*“multiple substitutions specified in non-positional format; did you mean to add the `formatted="false"`
attribute?”*), so the resource set of this branch did not compile.

* `formatted="false"` was added to that string in the English source **and in all 13 locales**.
* `scripts/localization_check.py` now contains a port of AOSP `aapt2`'s `util::VerifyJavaStringFormat`
  (`tools/aapt2/util/Util.cpp`) and reports the same failure for every translatable string that is not marked
  `formatted="false"`. Removing the attribute from the English source or from any locale now makes the checker
  fail with exit code 1 instead of reporting zero errors.

### P1 — user-visible copy was still hardcoded in Kotlin

The review listed the remaining hardcoded strings; all of them now live in string resources and are translated in
every locale:

| Was hardcoded in | Now |
|---|---|
| `UserProfileScreen.kt` — “User Profile”, “Profile photo options”, “Profile Photo”, “Choose the photo used across the app”, “Chat/Side Drawer/Menu Screen Background”, “Bottom Sheet Background”, “Dialog Background”, “%s options”, “%s (unavailable)”, “Unable to use image”, “OK”, “Photos Gallery”, “Media Storage”, “Remove”, “Close”, “Search photos”, “No photos in Media Storage” | `user_profile_*` resources (15 new keys); “Appearance”, “Media Storage”, “Remove”, “Close” and “OK” reuse the already translated `settings_tab_appearances`, `settings_storage`, `common_remove`, `common_close` and `common_ok`. |
| `ToolsEditor.kt` and `FeatureToolsSheet.kt` — “Search Tools”, “Find tool names and enabled states” | `tool_search_tools_title`, `tool_search_tools_description` (same pattern as the sibling `tool_agent_skills_search_*`). |
| `OllamaAdapter.kt` — the chat error prefix “ [Error: … ]” and its “Network error” fallback | `ollama_error_text`, `ollama_network_error`, resolved through the application context. |

The checker gained a third rule, `check_hardcoded_ui_text`, which scans `app/src/main/kotlin` and fails on user
visible copy that is still hardcoded, so the finding cannot come back unnoticed.

## Verification record

Run on 2026-10-27 from the repository root.

### Coverage — `python3 scripts/localization_check.py` (exit code 0)

Source inventory: **1,221 `<string>`** entries = **1,220 translatable** + **1 invariant** (`remote_machine_sftp_root_summary`),
plus **1 `<plurals>`** resource (`media_chain_profile_steps`).

| Folder | Tag | Required | Translated | Coverage | Errors | Warnings (identical-to-English, reviewed) |
|---|---|---|---|---|---|---|
| `values-de` | de | 1220 | 1220 | 100.0% | 0 | 4 |
| `values-es` | es | 1220 | 1220 | 100.0% | 0 | 3 |
| `values-fr` | fr | 1220 | 1220 | 100.0% | 0 | 5 |
| `values-in` | id | 1220 | 1220 | 100.0% | 0 | 2 |
| `values-it` | it | 1220 | 1220 | 100.0% | 0 | 2 |
| `values-ja` | ja | 1220 | 1220 | 100.0% | 0 | 3 |
| `values-ko` | ko | 1220 | 1220 | 100.0% | 0 | 4 |
| `values-pt-rBR` | pt-BR | 1220 | 1220 | 100.0% | 0 | 3 |
| `values-ru` | ru | 1220 | 1220 | 100.0% | 0 | 2 |
| `values-th` | th | 1220 | 1220 | 100.0% | 0 | 2 |
| `values-vi` | vi | 1220 | 1220 | 100.0% | 0 | 2 |
| `values-zh-rCN` | zh-CN | 1220 | 1220 | 100.0% | 0 | 4 |
| `values-zh-rTW` | zh-TW | 1220 | 1220 | 100.0% | 0 | 4 |

**Totals: zero missing required keys, zero unexpected obsolete keys, zero duplicate keys, zero broken format
arguments, zero broken Android escapes, zero invalid XML, zero strings aapt2 would refuse, zero hardcoded
user-visible strings in Kotlin, correct plural categories everywhere.**
Every locale also emits the `media_chain_profile_steps` plural with the categories its CLDR rules require:
`one`+`other` (de/es/fr/id/it/pt-BR), `one`+`few`+`many`+`other` (ru), `other` only (ja/ko/th/vi/zh-CN/zh-TW).

### What the checker verifies

`scripts/localization_check.py` (new, offline, no network) compares every locale against the English inventory and
fails on: missing required keys, keys that no longer exist in the source, duplicate keys, format-argument drift
(missing, extra or re-indexed `%s`/`%d`), Android escape drift (`\n`, `\t`, `\'`, `\"`), unescaped quotes, differing
plural categories, and malformed XML. It deliberately does **not** treat an English fallback as coverage — a key that
is absent from the locale file is reported as missing. `scripts/translate_strings.py --check` is the same check behind
the existing script, so it can be run without `googletrans` installed.

Two further rules were added after the pull request review:

| Rule | What it refuses | Why |
|---|---|---|
| `verify_java_string_format` | A translatable string that is not marked `formatted="false"` and that carries more than one argument with at least one non-positional argument (`%h`, `%p`, `%s`, …). | It is a port of AOSP `aapt2`'s `util::VerifyJavaStringFormat` (`tools/aapt2/util/Util.cpp`), which rejects exactly this shape with *“multiple substitutions specified in non-positional format”*. Without the rule the checker reported zero errors on a resource set that `aapt2 compile` refuses. |
| `check_hardcoded_ui_text` | User-visible copy hardcoded in Kotlin (`Text("…")`, `contentDescription = "…"`) that still contains translatable words. | Such a literal stays English under every locale, so it defeats the whole plan. The scan ignores comments, template expressions, symbols and identifiers, so only real copy is reported. |

The XML parser behind the checker also refuses documents that declare a document type or an entity, so no
resource file can expand an external or internal entity while the checker reads it.

### Checker refactor — equivalence check (executed)

The `aapt2` rule was split into small functions (`read_format_argument`, `read_argument_index`,
`skip_size_and_flags`) to keep every method readable. The new implementation was compared with the previous
one over **every string of the 14 resource files plus 120,000 generated format strings**: **0 differences**,
so the refactor did not change what the checker accepts.

### Added tests

`app/src/test/java/org/starfall/generative/AppLanguageSelectionTest.kt` (JUnit 4 + Robolectric, `@Config(sdk = [28])`)
covers the language pick and the resources behind it:

| Test | Covers |
|---|---|
| `everyLanguageResolvesToItsOwnEntry`, `unknownTagsFallBackToEnglish` | `AppLanguage.fromTag` for all 14 tags and for unknown input |
| `closestLanguageMatchesTheSystemLocale`, `closestLanguageHandlesBothIndonesianSpellings`, `closestLanguagePicksTheChineseVariant`, `unsupportedLanguagesFallBackToEnglish` | `AppLanguage.closestTo` for every supported language, both Indonesian spellings (`id`/`in`) and the `zh` script/country variants |
| `resourceQualifiersPointAtTheTranslatedFolders` | `resourceQualifier` ↔ resource folder mapping, including the legacy `in` qualifier |
| `everyLanguageShipsItsOwnTranslatedStrings` | per-language resource resolution of `R.string.select_model` (English + 12 locales) |
| `indonesianStringsResolveFromTheLegacyInQualifier` | Indonesian resolution from `values-in` (asserted through the resource qualifier, because `in` is the legacy language code Android keeps for Indonesian) |
| `languageNamesAreShownInTheAppLanguage`, `indonesianLanguageNamesResolveFromTheLegacyInQualifier` | the `language_name_*` resources resolve to the name of each language in the app language |
| `everyLocaleProvidesTheProfileStepsPlural`, `indonesianProvidesTheProfileStepsPlural` | plural safety of `media_chain_profile_steps` in every locale |
| `savedLanguageOverrideDrivesTheWrappedContext`, `followingTheSystemLanguageKeepsTheBaseContext`, `preferencesMirrorTheLanguageChoiceIntoTheOverride` | `AppLocale.save`/`overrideTag`/`wrap` and `AppPreferencesRepository.setAppLanguage` |

### Indonesian resource qualifier

Indonesian ships in `values-in/`, the qualifier Android and the AOSP framework still use for Indonesian
(`AppLanguage.INDONESIAN.resourceQualifier == "in"`), while the language tag stays the BCP-47 `id`. `closestTo`
normalizes both spellings to the same entry, and the tests assert the folder is wired correctly in both ways.

### Not run in this environment

| Check | Status | Reason |
|---|---|---|
| Gradle build / `assembleDebug` | **not run** | No JDK (`java -version` fails), no `$ANDROID_HOME`/`$ANDROID_SDK_ROOT`, no cached Gradle wrapper distribution. Installing a toolchain is out of scope for this sandbox. |
| Android lint | **not run** | Same reason — lint needs the Android SDK and Gradle. |
| JVM unit tests (`AppLanguageSelectionTest`) | **not run** | Same reason — no JDK/Gradle to execute JUnit/Robolectric. The test file is committed and ready to run in a normal environment. |
| Device/emulator UI QA | **not run** | No emulator image or device available. Narrow-phone widths, large font scale, Thai wrapping, CJK truncation and accessibility labels still need a manual pass. |
| `aapt2 compile` / `aapt2 link` | **not run** | No Android SDK in this sandbox. The `formatted="false"` fix and the checker's `aapt2` rule are derived from the AOSP source of `util::VerifyJavaStringFormat` and from the reviewer's own `aapt2` run. |
| `aapt2` rule of the checker | **executed** | The port of `util::VerifyJavaStringFormat` was run over all 14 resource files and over deliberately broken copies (attribute removed from the English source and from `values-vi`): the checker exits 1 with the expected diagnostic and exits 0 with the attribute in place. |

### Manual follow-up still recommended

1. Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` once in a full toolchain environment.
2. Switch each of the 14 languages explicitly and once via the system locale; confirm no unexpected English fallback
   and that the locale survives a relaunch.
3. Walk Settings, provider/model editors, tool/skill screens, Chat, media preview, Remote Machines, dialogs,
   notifications and accessibility labels on a narrow phone with a large font scale.

### Files changed

| Path | Change |
|---|---|
| `app/src/main/res/values/strings.xml` | Removed the `translatable="false"` flags from user-facing strings; only `remote_machine_sftp_root_summary` stays invariant. |
| `app/src/main/res/values-{de,es,fr,ja,ko,pt-rBR,ru,vi,zh-rCN,zh-rTW}/strings.xml` | Completed to 1,220/1,220 keys and the plural; existing entries reviewed. |
| `app/src/main/res/values-{it,th,in}/strings.xml` | New locales, 1,220/1,220 keys and the plural. |
| `app/src/main/kotlin/org/starfall/generative/shared/storage/preferences/AppLanguage.kt` | 14 languages (added Italian, Thai, Indonesian and Russian), `resourceQualifier`, `id`/`in` normalization. |
| `app/src/test/java/org/starfall/generative/AppLanguageSelectionTest.kt` | New targeted tests for locale selection, resource resolution and plural safety. |
| `app/src/main/res/values*/strings.xml` | Added the 14 `language_name_*` strings to the English source and to every locale. |
| `app/src/main/res/values*/strings.xml` | Added `formatted="false"` to `remote_machine_websocket_url_help` and the 19 new `user_profile_*`, `tool_search_tools_*` and `ollama_*` strings to the English source and to every locale. |
| `app/src/main/kotlin/org/starfall/generative/features/settings/ui/AppSettings.kt` | The language picker shows the name in the app language above the native name. |
| `app/src/main/kotlin/org/starfall/generative/features/userprofile/ui/UserProfileScreen.kt` | Hardcoded profile copy replaced with string resources. |
| `app/src/main/kotlin/org/starfall/generative/features/tools/ui/ToolsEditor.kt`, `features/home/shared/ui/FeatureToolsSheet.kt` | Hardcoded “Search Tools” copy replaced with string resources. |
| `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/ollama/OllamaAdapter.kt` | The chat error prefix and its network-error fallback come from string resources. |
| `scripts/localization_check.py` | New offline coverage/validation CLI, hardened against XML entity attacks, extended with the `aapt2` format rule and the hardcoded-Kotlin-text rule. |
| `scripts/translate_strings.py` | `--check` delegates to the new checker (offline, no `googletrans` needed). |

## Speech engines and Music Generation — completed localization

Added the 21 speech/music strings to every supported non-English locale in one coordinated change:
`de`, `es`, `fr`, `in`, `it`, `ja`, `ko`, `pt-rBR`, `ru`, `th`, `vi`, `zh-rCN`, and `zh-rTW`.
The translations cover voice browsing/search, engine model/key labels, Music Generation and its profile library, protected credentials and validation messages. Android Keystore, API, IDs and `{{api_token}}` retain their technical meaning; profile terminology follows each locale's existing media-profile labels.

`python3 scripts/localization_check.py --format json` passes with **1,238/1,238 required entries (100%) in every locale**, with zero errors. The checker still warns about some deliberately identical technical terms and existing strings. The missing-translation follow-up for speech/music is resolved.

Feature Tools now presents Image Generation, Video Generation, then Music Generation. Missing model/profile selections no longer produce inline red setup warnings; execution-time validation is retained.

## Invariant reasoning effort labels (2026-10-28)

At the user's request, reasoning effort level/state labels remain in English across all locales. Removed the four localized overrides (`reasoning_level_xhigh`, `model_reasoning_level_min`, `model_reasoning_level_med`, `model_reasoning_level_xhigh`) from every non-English resource file and marked the English originals non-translatable. Dedicated non-translatable resources also cover None, Default and Active within reasoning controls; common labels elsewhere retain their translations. Low, High and the other API level names already use their original names. These invariant labels are excluded from required translation coverage.

## Media generation failure accessibility (2026-10-28)

Added `media_image_generation_failed` and `media_video_generation_failed` to English and every supported non-English locale. These labels are screen-reader descriptions for the retained failed-generation tiles; the tiles visibly show only the Fluent ErrorCircle icon.

## Configurable speech chunks (2026-10-28)

Added `speech_sentences_per_chunk` and `speech_chunk_boundaries` to English and every supported non-English locale together. They label the per-service API sentence-count setting and explain deduplication of adjacent period/newline boundaries.

## Speech playback notifications (2026-10-28)

Added `notification_speech_channel` and `notification_speech_playing` in English and all 13 supported non-English locales. The notification reuses the existing localized Stop action and displays the configured service name without the transcript.
