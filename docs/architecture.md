# UI Architecture

## Design Language

MultiGateway uses **Jetpack Compose and Material 3**, with app-specific layouts and **Fluent UI System Icons**. The UI should feel clean, content-first, and consistent rather than decorating every area with extra containers.

- Use `MultiGatewayTheme`, `MaterialTheme.colorScheme`, and `MaterialTheme.typography` for colors and text. Respect system/light/dark themes, dynamic colors, custom palettes, and AMOLED mode; avoid hard-coded colors and typography when a theme token exists.
- Keep clear hierarchy through spacing, typography, and restrained surface contrast. Reuse shared cards, settings sections, top bars, dialogs, bottom sheets, and transitions before creating new variants. Use sensible list-based layouts for long content and keep chat focused on conversation content.
- Prefer shared UI components under `shared/ui` over duplicated implementations. Use string resources for user-facing text so screens remain localizable.

The existing UI generally follows this direction, but there are legacy icon aliases, direct drawable references, and occasional hard-coded text. New work should favor shared components and consistent resource usage without unrelated rewrites.

## Icons

Source: [Fluent UI System Icons](https://composables.com/icons/icon-libraries/fluentui-system-icons).

- Prefer downloading **individual Kotlin Compose `ImageVector` sources** with `python scripts/get_icon.py <icon>`; they live under `shared/ui/icons/fluentui/<style>/` and are registered automatically in `FluentUi.Regular`, `FluentUi.Filled`, and the corresponding style object. Do not add the whole icon library as a Gradle dependency.
- Use **Regular** as the default for common actions and navigation. Use **Light** when a deliberately lighter stroke suits the context. Keep the style consistent among related actions and states; do not mix styles arbitrarily.
- Use `FluentIcon(FluentUi.Regular.ArrowClockwise, ...)` or `Icon(imageVector = FluentUi.Regular.ArrowClockwise, ...)` for Kotlin-vector usage. `Icons.Outlined`, `Icons.Default`, `Icons.Filled`, and `Icons.AutoMirrored` preserve older Material symbol names while mapping Fluent UI icons to `ImageVector`. Keep Fluent icons as individual Kotlin sources; do not reintroduce Fluent drawable XMLs. Non-Fluent icons such as `Terminal` may remain drawable-backed.
- Tint with `MaterialTheme.colorScheme`; avoid hard-coded icon colors. Icon-only controls need a meaningful content description, while decorative icons or icons next to an equivalent label should not repeat it.
- Choose icons by meaning and clarity, not merely appearance. Keep the app logo and other branded artwork separate from action icons.
