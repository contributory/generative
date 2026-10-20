#!/usr/bin/env python3
"""Download one Android Vector Drawable icon from composables.com."""

from __future__ import annotations

import argparse
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

BASE_URL = "https://composables.com/download/icons"
REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUTPUT_DIR = REPO_ROOT / "app" / "src" / "main" / "res" / "drawable"

# Short names are convenient on the command line; full Composables slugs also work.
LIBRARY_ALIASES = {
    "fluentui": "fluentui-system-icons",
    "material": "material-icons",
    "fontawesome": "font-awesome",
    "simpleicons": "simple-icons",
}
SLUG_PATTERN = re.compile(r"^[a-z0-9][a-z0-9-]*$")


def parse_library(value: str) -> tuple[str, str | None, str]:
    """Return the Composables slug, optional style, and short output prefix."""
    parts = value.strip().lower().split("/", maxsplit=1)
    if len(parts) > 2 or any(not SLUG_PATTERN.fullmatch(part) for part in parts):
        raise argparse.ArgumentTypeError(
            "library must be <library> or <library>/<style>, e.g. fluentui/regular"
        )

    requested_name = parts[0]
    library_slug = LIBRARY_ALIASES.get(requested_name, requested_name)
    style = parts[1] if len(parts) == 2 else None
    output_prefix = "fluent" if library_slug == "fluentui-system-icons" else library_slug.replace("-", "_")
    return library_slug, style, output_prefix


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Download an individual icon from composables.com as Android Vector Drawable XML. "
            "By default, saves it to app/src/main/res/drawable/ in this repository."
        ),
        epilog=(
            "Examples:\n"
            "  python scripts/get_icon.py add\n"
            "  python scripts/get_icon.py --lib fluentui/regular arrow-clockwise\n"
            "  python scripts/get_icon.py --lib fluentui/filled --output /tmp/icons add\n"
            "  python scripts/get_icon.py --lib lucide check --output /tmp/check.xml"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "icon",
        help="Icon slug used by Composables, e.g. add, arrow-clockwise, or document-text",
    )
    parser.add_argument(
        "--lib",
        default="fluentui/regular",
        metavar="LIBRARY/STYLE",
        help="Composables icon library and optional style (default: fluentui/regular)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="Output XML file or directory (default: app/src/main/res/drawable/)",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Overwrite the output file if it already exists",
    )
    return parser


def output_filename(prefix: str, icon: str, style: str | None) -> str:
    icon_name = icon.replace("-", "_")
    if prefix == "fluent":
        suffix = f"_24_{style.replace('-', '_')}" if style else "_24"
        return f"ic_fluent_{icon_name}{suffix}.xml"

    suffix = f"_{style.replace('-', '_')}" if style else ""
    return f"ic_{prefix}_{icon_name}{suffix}.xml"


def download_icon(library: str, style: str | None, icon: str) -> str:
    encoded_library = urllib.parse.quote(library, safe="-")
    encoded_icon = urllib.parse.quote(icon, safe="-")
    url = f"{BASE_URL}/{encoded_library}/{encoded_icon}.xml"
    if style:
        url += "?" + urllib.parse.urlencode({"v": style})

    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "MultiGateway-get-icon/1.0 (+https://composables.com/icons)",
            "Accept": "application/xml,text/xml;q=0.9,*/*;q=0.8",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            content_type = response.headers.get_content_type()
            content = response.read().decode("utf-8-sig")
    except urllib.error.HTTPError as error:
        if error.code == 404:
            raise RuntimeError(
                f"Icon not found: {icon!r} in library {library!r}"
                + (f" (style {style!r})" if style else "")
            ) from error
        raise RuntimeError(f"Composables returned HTTP {error.code} for {url}") from error
    except (urllib.error.URLError, TimeoutError) as error:
        raise RuntimeError(f"Could not download icon from Composables: {error}") from error

    # Composables currently emits @color/black, which is not defined by this app.
    # Keep their license comment and path data, but use a self-contained vector color.
    content = content.replace("@color/black", "#FF000000").replace("@color/white", "#FFFFFFFF")
    try:
        root = ET.fromstring(content)
    except ET.ParseError as error:
        hint = f" (HTTP content type: {content_type})" if content_type else ""
        raise RuntimeError(
            f"Composables did not return valid XML for {icon!r}{hint}; check the library and icon name"
        ) from error

    if root.tag != "vector":
        raise RuntimeError(
            f"Composables returned XML with root <{root.tag}>, not an Android <vector>; refusing to save it"
        )
    return content.rstrip() + "\n"


def resolve_output(output: Path | None, filename: str) -> Path:
    if output is None:
        return DEFAULT_OUTPUT_DIR / filename
    if output.exists() and output.is_dir():
        return output / filename
    if output.suffix.lower() == ".xml":
        return output
    return output / filename


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    if not SLUG_PATTERN.fullmatch(args.icon):
        parser.error("icon must be a lowercase Composables slug containing only a-z, 0-9, and hyphens")

    try:
        library, style, prefix = parse_library(args.lib)
        filename = output_filename(prefix, args.icon, style)
        target = resolve_output(args.output, filename)

        if target.exists() and not args.force:
            raise RuntimeError(f"File already exists: {target} (use --force to overwrite it)")

        content = download_icon(library, style, args.icon)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8", newline="\n")
    except (argparse.ArgumentTypeError, OSError, RuntimeError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    print(f"Saved {args.lib}/{args.icon} -> {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
