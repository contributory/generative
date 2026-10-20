#!/usr/bin/env python3
"""Download a Compose ImageVector icon from composables.com and register Fluent UI icons."""

from __future__ import annotations

import argparse
import concurrent.futures
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

BASE_URL = "https://composables.com/download/icons"
REPO_ROOT = Path(__file__).resolve().parent.parent
KOTLIN_ROOT = REPO_ROOT / "app" / "src" / "main" / "kotlin" / "org" / "starfall" / "generative" / "shared" / "ui" / "icons"
REGISTRY = KOTLIN_ROOT / "FluentUi.kt"
LIBRARY_ALIASES = {
    "fluentui": "fluentui-system-icons",
    "material": "material-icons",
    "fontawesome": "font-awesome",
    "simpleicons": "simple-icons",
}
SLUG_PATTERN = re.compile(r"^[a-z0-9][a-z0-9-]*$")
MARKER_START = "    // <generated-icons:{style}:start>"
MARKER_END = "    // <generated-icons:{style}:end>"


def pascal_case(slug: str) -> str:
    return "".join(part[:1].upper() + part[1:] for part in re.split(r"[-_]", slug) if part)


def parse_library(value: str) -> tuple[str, str | None, str]:
    parts = value.strip().lower().split("/", maxsplit=1)
    if len(parts) > 2 or any(not SLUG_PATTERN.fullmatch(part) for part in parts):
        raise argparse.ArgumentTypeError("library must be <library> or <library>/<style>, e.g. fluentui/regular")
    requested = parts[0]
    slug = LIBRARY_ALIASES.get(requested, requested)
    style = parts[1] if len(parts) == 2 else None
    if style and not SLUG_PATTERN.fullmatch(style):
        raise argparse.ArgumentTypeError("style must be a lowercase slug, e.g. regular or filled")
    if style == "colored":
        style = "color"
    if slug == "fluentui-system-icons" and style not in {"regular", "filled", "light", "color"}:
        raise argparse.ArgumentTypeError("Fluent UI icons require --lib fluentui/<regular|filled|light|color>")
    return slug, style, "fluentui" if slug == "fluentui-system-icons" else slug.replace("-", "_")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Download an individual Kotlin Compose ImageVector from composables.com.",
        epilog=(
            "Examples:\n"
            "  python scripts/get_icon.py arrow-clockwise\n"
            "  python scripts/get_icon.py --lib fluentui/filled add\n"
            "  python scripts/get_icon.py --lib fluentui/regular arrow-clockwise --force"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("icon", help="Icon slug used by Composables, e.g. add or arrow-clockwise")
    parser.add_argument("--lib", default="fluentui/regular", metavar="LIBRARY/STYLE",
                        help="Icon library and style (default: fluentui/regular)")
    parser.add_argument("--output", type=Path,
                        help="Output .kt file or directory (default: shared/ui/icons/fluentui/<style>/)")
    parser.add_argument("--force", action="store_true", help="Overwrite an existing Kotlin file")
    return parser


def download_icon(library: str, style: str | None, icon: str) -> str:
    url = f"{BASE_URL}/{urllib.parse.quote(library, safe='-')}/{urllib.parse.quote(icon, safe='-')}.kt"
    if style:
        url += "?" + urllib.parse.urlencode({"v": style})
    request = urllib.request.Request(url, headers={
        "User-Agent": "Generative-get-icon/1.0 (+https://composables.com/icons)",
        "Accept": "text/x-kotlin,text/plain,*/*;q=0.8",
    })
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            content = response.read().decode("utf-8-sig")
    except urllib.error.HTTPError as error:
        if error.code == 404:
            raise RuntimeError(f"Icon not found: {icon!r} in library {library!r} (style {style!r})") from error
        raise RuntimeError(f"Composables returned HTTP {error.code} for {url}") from error
    except (urllib.error.URLError, TimeoutError) as error:
        raise RuntimeError(f"Could not download icon from Composables: {error}") from error

    if "ImageVector.Builder(" not in content or not re.search(r"\bval\s+\w+\s*:\s*ImageVector", content):
        raise RuntimeError(f"Composables did not return a Kotlin ImageVector for {icon!r}; refusing to save it")
    return "\n".join(line.rstrip() for line in content.splitlines()).rstrip() + "\n"


def resolve_output(output: Path | None, library: str, style: str | None, icon: str) -> Path:
    if output is None:
        if library == "fluentui-system-icons":
            return KOTLIN_ROOT / "fluentui" / (style or "regular") / f"{pascal_case(icon)}.kt"
        suffix = f"/{style}" if style else ""
        return KOTLIN_ROOT / "generated" / library.replace("-", "_") / suffix.strip("/") / f"{pascal_case(icon)}.kt"
    if output.exists() and output.is_dir():
        return output / f"{pascal_case(icon)}.kt"
    if output.suffix.lower() == ".kt":
        return output
    return output / f"{pascal_case(icon)}.kt"


def package_for(target: Path) -> str:
    try:
        relative = target.parent.relative_to(KOTLIN_ROOT)
    except ValueError:
        # Keep --output useful for temporary exports and tests outside the project.
        return "org.starfall.generative.shared.ui.icons"
    return "org.starfall.generative.shared.ui.icons" + ("." + ".".join(relative.parts) if relative.parts else "")


def add_package(content: str, package: str) -> str:
    if re.search(r"^\s*package\s+", content, re.MULTILINE):
        raise RuntimeError("Downloaded Kotlin already declares a package; refusing to rewrite it automatically")
    return f"package {package}\n\n" + content


def update_registry(style: str, icon: str) -> None:
    """Regenerate the typed FluentUi registry from downloaded icon source files."""
    if style not in {"regular", "filled", "light", "color"}:
        return
    if not REGISTRY.exists():
        raise RuntimeError(f"Fluent UI registry is missing: {REGISTRY}")

    styles = ("regular", "filled", "light", "color")
    imports: list[str] = []
    objects: list[str] = []
    for item_style in styles:
        source_dir = KOTLIN_ROOT / "fluentui" / item_style
        props: list[tuple[str, str]] = []
        if source_dir.exists():
            for source in sorted(source_dir.glob("*.kt"), key=lambda path: path.stem.lower()):
                prop = source.stem
                source_text = source.read_text(encoding="utf-8")
                match = re.search(r"\bval\s+(\w+)\s*:\s*ImageVector", source_text)
                if not match:
                    raise RuntimeError(f"Could not find an ImageVector declaration in {source}")
                source_symbol = match.group(1)
                alias = f"{prop}FluentVector"
                imports.append(
                    f"import org.starfall.generative.shared.ui.icons.fluentui.{item_style}.{source_symbol} as {alias}"
                )
                props.append((prop, alias))
        objects.append(f"    object {item_style.title()} {{")
        objects.append(f"        // <generated-icons:{item_style}:start>")
        objects.extend(f"        val {prop}: ImageVector get() = {alias}" for prop, alias in props)
        objects.append(f"        // <generated-icons:{item_style}:end>")
        objects.append("    }")

    header = (
        "package org.starfall.generative.shared.ui.icons\n\n"
        + "\n".join(sorted(imports))
        + "\n\nimport androidx.compose.ui.graphics.vector.ImageVector\n\n"
        + "/** Typed Compose icons downloaded individually from Composables; compatibility names are exposed through [Icons]. */\n"
        + "object FluentUi {\n"
        + "\n".join(objects)
        + "\n}\n"
    )
    REGISTRY.write_text(header, encoding="utf-8", newline="\n")


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    if not SLUG_PATTERN.fullmatch(args.icon):
        parser.error("icon must be a lowercase Composables slug containing only a-z, 0-9, and hyphens")
    try:
        library, style, _ = parse_library(args.lib)
        target = resolve_output(args.output, library, style, args.icon)
        if target.exists() and not args.force:
            raise RuntimeError(f"File already exists: {target} (use --force to overwrite it)")
        content = download_icon(library, style, args.icon)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(add_package(content, package_for(target)), encoding="utf-8", newline="\n")
        if library == "fluentui-system-icons" and target.parent.parent == KOTLIN_ROOT / "fluentui":
            update_registry(style or "regular", args.icon)
    except (argparse.ArgumentTypeError, OSError, RuntimeError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    print(f"Saved {args.lib}/{args.icon} -> {target}")
    if library == "fluentui-system-icons":
        print(f"Use: FluentUi.{(style or 'regular').title()}.{pascal_case(args.icon)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
