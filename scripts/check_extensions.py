#!/usr/bin/env python3
"""Check standalone extension imports without needing the Android toolchain."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
EXTENSIONS = ROOT / "app/src/main/kotlin/org/starfall/generative/extensions"
IMPORT = re.compile(r"^\s*import\s+([\w.*]+)", re.MULTILINE)
APP_PREFIX = "org.starfall.generative."
ALLOWED_PREFIX = APP_PREFIX + "extensions."
BACKENDS = (
    "androidx.room.", "androidx.sqlite.", "android.database.", "java.sql.",
    "okhttp3.", "retrofit2.", "io.ktor.client.",
    "java.net.HttpURLConnection", "java.net.URLConnection", "java.net.Socket",
    "java.net.ServerSocket", "java.net.DatagramSocket",
    "java.io.File", "java.io.RandomAccessFile", "java.nio.file.Files",
)


def violations(source: str):
    for match in IMPORT.finditer(source):
        name = match.group(1)
        external_app = name.startswith(APP_PREFIX) and not name.startswith(ALLOWED_PREFIX)
        backend = any(name.startswith(prefix) for prefix in BACKENDS)
        if external_app or backend:
            line = source.count("\n", 0, match.start()) + 1
            yield line, name


def main():
    if not EXTENSIONS.is_dir():
        print(f"Missing extensions directory: {EXTENSIONS}", file=sys.stderr)
        return 1
    files = sorted(EXTENSIONS.rglob("*.kt"))
    errors = []
    for path in files:
        for line, name in violations(path.read_text()):
            errors.append(f"{path.relative_to(ROOT)}:{line}: forbidden extension import: {name}")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Extension dependency boundary passed ({len(files)} Kotlin files).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
