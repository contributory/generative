# Agent Instructions

Before editing the project, inspect the current workspace and read this file. Follow the project structure, dependency-layer, naming, and source-file organization rules documented in [`docs/structure.md`](docs/structure.md), and follow the UI design and icon conventions in [`docs/architecture.md`](docs/architecture.md).

Preserve existing behavior and unrelated user changes. Make focused source edits rather than rewriting whole files or removing code merely to reduce line count. Do work one item at a time from easy to hard. Do not commit, push, or build unless explicitly asked. Run lint and tests together after the implementation is complete rather than repeatedly invoking Gradle for lint during the work.

## Architecture rules

Treat these as mandatory constraints; `docs/structure.md` contains the full package map and supporting details.

- **Dependency direction:** `shared` ← `integrations` ← `features` ← `navigation` ← `appwiring`. A layer may depend only on layers to its left. `shared/` and `integrations/` must never import `features/`; feature packages must not import each other. `home/` is a feature under `features/`, not a peer layer.
- **Place code by responsibility and usage.** Keep code in the closest layer that uses it. Move code to root `shared/` only when it is genuinely reused across features/layers; external API and protocol communication belongs in `integrations/`. Helpers prepare inputs or interpret outputs and must not import `integrations/`. Models must not import UI, logic, or helpers.
- **Features are vertical slices, not screen registries.** A `features/<name>/` package represents a user/business capability and owns the presentation, feature-level orchestration/use-case flow, and feature-specific state/models for that capability. It can contain multiple screens, components, and logic that are not directly tied to a single menu destination. `FeaturesScreen`/`AppMenuScreen` entries describe how some capabilities are exposed in the UI; they do not define the feature boundary.
- **Within a feature:** UI may depend on logic and model; logic may depend on model; model must not depend on UI or logic. `ui/` contains Compose rendering and user interaction, `logic/` contains ViewModels/coordinators/feature workflows and must not contain `@Composable` or import `androidx.compose.material3`, and `model/` contains feature-specific data/state types. Not every feature needs all three folders. Sub-features must not import each other; their shared code belongs in that feature's `shared/`.
- **Navigation:** transitions between screens go through callbacks wired in `navigation/AppNavigation.kt`, not direct imports of another screen.
- **File and component naming:** use PascalCase for Kotlin source files and Compose components. A UI file should normally match its primary public composable/type. Follow the component-role naming table in `docs/structure.md`; do not use screen, drawer, sidebar, sheet, and dialog names interchangeably. When renaming, update filenames, declarations, imports, navigation, previews, tests, and the structure map together.
- **File size:** aim for 500 lines or fewer per source file, including blank lines and comments. This is a soft limit: split only when it improves cohesion and avoids needless fragmentation.
- Before moving code or changing package structure, inspect callers, imports, tests, and package references. Preserve behavior and unrelated changes; do not create duplicate implementations to avoid updating references.

## Language and localization

- English is the canonical source language. Keep its user-facing text and documentation complete and up to date; adding or updating English alone is always allowed.
- For non-English translations, either add or update **all supported non-English locales** consistently in the same change, or add/update **none** of them. Never add or update only some non-English languages, even if several are changed together. Do not let AI arbitrarily select a subset of languages for translation. If all supported locales cannot be handled in the same change, leave non-English translations untouched and defer them to a coordinated localization change.
- This policy does not rank non-English languages by importance. When localization is undertaken, all supported non-English languages must receive equal consideration, comparable completeness, and feature parity. Translations should be natural and accurate for each locale, not literal word-for-word copies.
- Keep source identifiers, technical documentation, code comments, and maintainer-facing project text in clear, consistent English unless a file or task explicitly requires another language.

## Text input and IME behavior

- **Human-readable text fields** (chat messages, prompts, instructions, titles, display names, labels, folder names, descriptions, and natural-language search queries) must auto-capitalize the first letter using sentence capitalization (`KeyboardCapitalization.Sentences`, the default in `SelectableOutlinedTextField` and `HeaderNameField`) and preserve normal IME composition.
- **Non-prose / literal input fields** (URLs, API keys, tokens, secrets, passwords, model or resource IDs, hostnames, file paths, shell commands, HTTP header names/values, JSON/code/template literals, and numeric values) are the only text fields that should omit auto-capitalization: use `KeyboardCapitalization.None`, disable autocorrection/suggestions (`autoCorrectEnabled = false`), and disable IME text composition (for example with `KeyboardType.Password` paired with `VisualTransformation.None` when plain text should remain visible, or `KeyboardType.Number` for numeric inputs) so keyboards deliver raw literal input without IME composing transformations.
