#!/usr/bin/env python3
"""Offline coverage and validation for the translated Android string resources.

The English `values/strings.xml` is the canonical inventory: every translatable key it declares
must exist in every supported locale, with the same format arguments and the same Android escapes.
This tool never treats an English fallback as translated coverage; a missing key is a missing key.

Usage:
    python3 scripts/localization_check.py                 # report every locale
    python3 scripts/localization_check.py --locale de     # report one locale
    python3 scripts/localization_check.py --strict        # exit 1 on warnings too
    python3 scripts/localization_check.py --format json   # machine readable report

Exit code is 0 when no error was found, 1 otherwise.
"""
import argparse
import io
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

RES_DIR = Path(__file__).resolve().parent.parent / "app/src/main/res"
KOTLIN_DIR = Path(__file__).resolve().parent.parent / "app/src/main/kotlin"
SOURCE_DIR = "values"

# Locale folder suffix -> BCP-47 language tag used by AppLanguage.
LOCALES = {
    "de": "de",
    "es": "es",
    "fr": "fr",
    "in": "id",
    "it": "it",
    "ja": "ja",
    "ko": "ko",
    "pt-rBR": "pt-BR",
    "ru": "ru",
    "th": "th",
    "vi": "vi",
    "zh-rCN": "zh-CN",
    "zh-rTW": "zh-TW",
}

# CLDR plural categories a locale has to provide. Android falls back to `other`, so `other` is
# always required and the remaining categories are locale specific.
PLURAL_CATEGORIES = {
    "ru": {"one", "few", "many", "other"},
}
DEFAULT_PLURAL_CATEGORIES = {"one", "other"}
SINGULAR_ONLY_PLURAL_CATEGORIES = {"other"}

# Printf-style conversion specifiers and Android escapes.
FORMAT_SPECIFIER = re.compile(r"%(?:[0-9]+\$)?[-#+ 0,(]*[0-9]*(?:\.[0-9]+)?[a-zA-Z%]")
ANDROID_ESCAPE = re.compile(r"\\(?:n|t|'|\"|\\|u[0-9a-fA-F]{4})")
APOSTROPHE, QUOTE, BACKSLASH = chr(39), chr(34), chr(92)

# Conversion characters that turn a string into a Time.format() pattern; aapt2 accepts
# those as they are, so they end the format check right away.
TIME_FORMAT_CONVERSIONS = set("DFKMWZkmwyz")

# Compose call sites that render user visible text. A literal at one of these places stays
# English under every locale, so it has to live in a string resource instead.
UI_TEXT_SITES = (
    re.compile(r"\bText\(\s*(?:text\s*=\s*)?\"((?:[^\"\\]|\\.)*)\""),
    re.compile(r"\bcontentDescription\s*=\s*\"((?:[^\"\\]|\\.)*)\""),
)
# Template expressions such as "${count}" are runtime values, not hardcoded copy; the scan
# strips them before it looks for words. Vendored icon sources carry licence headers only.
SKIPPED_KOTLIN_FOLDERS = ("icons/fluentui",)


def has_unescaped_quote(text):
    """True when a quote character is not escaped with a backslash."""
    for index, char in enumerate(text):
        if char in (APOSTROPHE, QUOTE):
            if index == 0 or text[index - 1] != BACKSLASH:
                return True
    return False


class Problem:
    """One validation finding; `severity` is either `error` or `warning`."""

    def __init__(self, severity, kind, key, detail):
        self.severity = severity
        self.kind = kind
        self.key = key
        self.detail = detail

    def as_dict(self):
        return {"severity": self.severity, "kind": self.kind, "key": self.key,
                "detail": self.detail}

    def __str__(self):
        location = f"{self.key}: " if self.key else ""
        return f"[{self.severity}] {location}{self.detail}"


def parse_xml(path):
    """Read a strings.xml file into an element tree.

    The resources are the repository's own files, but the document is still hardened: a document
    type declaration is refused before parsing, so no entity can be declared, expanded or
    resolved from outside the file. The plain `ElementTree.parse` shortcut is avoided because the
    security scanners flag it (B314) as parsing untrusted XML.
    """
    data = Path(path).read_bytes()
    if b"<!DOCTYPE" in data.upper() or b"<!ENTITY" in data.upper():
        raise ET.ParseError("document type declarations are not allowed")
    tree = ET.ElementTree()
    tree.parse(io.BytesIO(data))
    return tree


def verify_java_string_format(text):
    """Port of aapt2's VerifyJavaStringFormat: is the string a valid format string?

    aapt2 refuses a translatable string that carries more than one substitution and at least one
    non-positional argument, because a translation may reorder the arguments. The check only
    applies to strings that are translatable and not marked formatted="false".
    """
    index, length = 0, len(text)
    arg_count = 0
    nonpositional = False
    while index < length:
        if text[index] == "%" and index + 1 < length:
            index += 1
            char = text[index]
            if char == "%" or char == "n":
                index += 1
                continue
            arg_count += 1
            digits = 0
            while index < length and text[index].isdigit():
                index += 1
                digits += 1
            if digits > 0:
                if index != length and text[index] != "$":
                    # The digits were a size, not a positional argument.
                    nonpositional = True
            elif index < length and text[index] == "<":
                # Reusing the last argument breaks when a translation reorders the arguments.
                nonpositional = True
                index += 1
                if index != length and text[index] == "$":
                    index += 1
            else:
                nonpositional = True
            # Ignore size, width and flags.
            while index < length and (text[index] in "-#+ ,(" or text[index].isdigit()):
                index += 1
            if index != length and text[index] in TIME_FORMAT_CONVERSIONS:
                return True
        if index < length:
            index += 1
    return not (arg_count > 1 and nonpositional)


def parse_resources(path):
    """Parse a strings.xml file into (strings, plurals, problems).

    `strings` maps a name to its text, `plurals` maps a name to {quantity: text}. The value of a
    `translatable` attribute is reported separately so the caller can tell invariant entries apart.
    """
    problems = []
    try:
        root = parse_xml(path).getroot()
    except ET.ParseError as error:
        problems.append(Problem("error", "xml", None, f"invalid XML: {error}"))
        return {}, {}, {}, problems

    strings, plurals, invariant = {}, {}, set()
    for element in root:
        name = element.get("name")
        if not name:
            problems.append(Problem("error", "xml", None, "element without a name attribute"))
            continue
        if element.get("translatable") == "false":
            invariant.add(name)
        if element.tag == "string":
            if name in strings:
                problems.append(Problem("error", "duplicate", name, "duplicate string key"))
            text = element.text or ""
            strings[name] = text
            if (element.get("translatable") != "false" and element.get("formatted") != "false"
                    and not verify_java_string_format(text)):
                problems.append(Problem(
                    "error", "format", name,
                    "aapt2 rejects this string: several substitutions in non-positional format; "
                    "use positional arguments (%1$s) or add formatted=\"false\""))
        elif element.tag == "plurals":
            if name in plurals:
                problems.append(Problem("error", "duplicate", name, "duplicate plurals key"))
            items = {}
            for item in element:
                quantity = item.get("quantity")
                if quantity in items:
                    problems.append(
                        Problem("error", "duplicate", name, f"duplicate quantity '{quantity}'"))
                items[quantity] = item.text or ""
            plurals[name] = items
        else:
            problems.append(Problem("error", "xml", name, f"unsupported element <{element.tag}>"))
    return strings, plurals, invariant, problems


def strip_kotlin_comments(source):
    """Blank out Kotlin comments while keeping the string literals and the line count intact."""
    out = []
    index, length = 0, len(source)
    in_line, in_block = False, False
    while index < length:
        char = source[index]
        if in_line:
            if char == "\n":
                in_line = False
                out.append(char)
            else:
                out.append(" ")
            index += 1
            continue
        if in_block:
            if char == "*" and index + 1 < length and source[index + 1] == "/":
                out.append("  ")
                index += 2
                in_block = False
                continue
            out.append("\n" if char == "\n" else " ")
            index += 1
            continue
        if char == '"':
            end = index + 1
            while end < length:
                if source[end] == "\\":
                    end += 2
                    continue
                if source[end] == '"':
                    end += 1
                    break
                end += 1
            out.append(source[index:end])
            index = end
            continue
        if char == "/" and index + 1 < length and source[index + 1] == "/":
            in_line = True
            out.append("  ")
            index += 2
            continue
        if char == "/" and index + 1 < length and source[index + 1] == "*":
            in_block = True
            out.append("  ")
            index += 2
            continue
        out.append(char)
        index += 1
    return "".join(out)


def strip_template_expressions(literal):
    """Remove Kotlin string templates, including nested braces, from a literal."""
    out = []
    index, length = 0, len(literal)
    while index < length:
        char = literal[index]
        if char == "$" and index + 1 < length and literal[index + 1] == "{":
            depth = 1
            index += 2
            while index < length and depth:
                if literal[index] == "{":
                    depth += 1
                elif literal[index] == "}":
                    depth -= 1
                index += 1
            continue
        if char == "$" and index + 1 < length and (literal[index + 1].isalpha() or literal[index + 1] == "_"):
            index += 1
            while index < length and (literal[index].isalnum() or literal[index] == "_"):
                index += 1
            continue
        out.append(char)
        index += 1
    return "".join(out)


def looks_like_prose(literal):
    """True when a literal carries words that have to be translated.

    Only the static part of the literal counts: template expressions hold runtime values.
    Symbols, file names and identifiers stay out of scope, so a literal is only reported when it
    contains a lowercase word of at least four letters.
    """
    if "://" in literal:
        return False
    static = strip_template_expressions(literal).replace("\\", "")
    for word in re.findall(r"[A-Za-z]{4,}", static):
        if any(char.islower() for char in word):
            return True
    return False


def check_hardcoded_ui_text(source_dir, problems):
    """Report user visible text that is hardcoded in Kotlin instead of localized."""
    if not source_dir or not source_dir.exists():
        return
    root = Path(__file__).resolve().parent.parent
    for path in sorted(Path(source_dir).rglob("*.kt")):
        if any(folder in path.as_posix() for folder in SKIPPED_KOTLIN_FOLDERS):
            continue
        source = path.read_text(encoding="utf-8", errors="replace")
        masked = strip_kotlin_comments(source)
        for pattern in UI_TEXT_SITES:
            for match in pattern.finditer(masked):
                literal = match.group(1)
                if not looks_like_prose(literal):
                    continue
                line = source.count("\n", 0, match.start()) + 1
                try:
                    location = path.relative_to(root).as_posix()
                except ValueError:
                    location = path.as_posix()
                problems.append(Problem(
                    "error", "hardcoded", f"{location}:{line}",
                    f'hardcoded user visible text "{literal}"; move it to a string resource '
                    "(R.string.*) and translate it"))


def format_specifiers(text):
    """Multiset of (index, conversion) pairs of a string."""
    found = []
    for match in FORMAT_SPECIFIER.finditer(text):
        token = match.group(0)
        if token == "%%":
            continue
        index = None
        conversion = token[-1]
        if "$" in token:
            index = token.split("$", 1)[0][1:]
        found.append((index, conversion))
    return Counter(found)


def escape_sequences(text):
    """Multiset of Android escape sequences used by a string."""
    return Counter(ANDROID_ESCAPE.findall(text))


def check_escaping(key, text, problems):
    """Android requires apostrophes and quotes to be escaped inside string resources."""
    if has_unescaped_quote(text):
        problems.append(Problem("error", "escape", key,
                                "unescaped apostrophe or quote; use \\' and \\\""))
    if text[:1] in ("@", "?"):
        problems.append(Problem("error", "escape", key,
                                "text starts with '@' or '?'; escape it as \\@ or \\?"))
    if "\n" in text or "\r" in text:
        problems.append(Problem("error", "escape", key, "raw newline in a string resource"))


def compare_with_source(key, source, translation, problems):
    """A translation must keep the source format arguments and escapes."""
    source_formats = format_specifiers(source)
    translated_formats = format_specifiers(translation)
    if source_formats != translated_formats:
        problems.append(Problem("error", "format", key,
                                f"format arguments differ: source {sorted(source_formats.items())}, "
                                f"translation {sorted(translated_formats.items())}"))
    source_escapes = escape_sequences(source)
    translated_escapes = escape_sequences(translation)
    for escape, count in source_escapes.items():
        if translated_escapes.get(escape, 0) != count:
            problems.append(Problem("error", "escape", key,
                                    f"escape sequence {escape} is not preserved"))
    if not translation.strip():
        problems.append(Problem("error", "empty", key, "empty translation"))
    if source == translation and len(source.strip()) >= 16 and " " in source.strip():
        problems.append(Problem("warning", "untranslated", key,
                                "identical to the English source"))


def plural_categories(locale):
    if locale in PLURAL_CATEGORIES:
        return PLURAL_CATEGORIES[locale]
    if locale in ("ja", "ko", "zh-CN", "zh-TW", "th", "vi", "id"):
        return SINGULAR_ONLY_PLURAL_CATEGORIES
    return DEFAULT_PLURAL_CATEGORIES


def check_locale(locale, folder, source_strings, source_plurals, required):
    """Validate one locale folder against the English inventory."""
    path = folder / "strings.xml"
    if not path.exists():
        return None, [Problem("error", "missing", None, f"{folder.name}/strings.xml is missing")]

    strings, plurals, _, problems = parse_resources(path)
    if not strings and not plurals:
        problems.append(Problem("error", "missing", None, "no string resources"))

    for key in required:
        if key not in strings:
            problems.append(Problem("error", "missing", key, "missing translation"))

    for key in strings:
        if key not in source_strings:
            problems.append(Problem("error", "obsolete", key, "not defined in the English source"))
    for key in plurals:
        if key not in source_plurals:
            problems.append(Problem("error", "obsolete", key, "not defined in the English source"))

    for key, text in strings.items():
        source = source_strings.get(key)
        if source is None:
            continue
        check_escaping(key, text, problems)
        compare_with_source(key, source, text, problems)

    for key, items in plurals.items():
        source_items = source_plurals.get(key)
        if source_items is None:
            continue
        expected = plural_categories(locale)
        missing = sorted(expected - set(items))
        if missing:
            problems.append(Problem("error", "plural", key,
                                    f"missing plural quantities: {', '.join(missing)}"))
        for quantity, text in items.items():
            check_escaping(key, text, problems)
            origin = source_items.get(quantity) or next(iter(source_items.values()))
            compare_with_source(key, origin, text, problems)

    return {"strings": strings, "plurals": plurals}, problems


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--res-dir", type=Path, default=RES_DIR)
    parser.add_argument("--kotlin-dir", type=Path, default=KOTLIN_DIR,
                        help="Kotlin sources scanned for hardcoded user visible text")
    parser.add_argument("--locale", help="only this locale (folder qualifier or language tag)")
    parser.add_argument("--format", choices=["text", "json"], default="text")
    parser.add_argument("--strict", action="store_true",
                        help="exit with code 1 when warnings are found too")
    args = parser.parse_args()

    source_path = args.res_dir / SOURCE_DIR / "strings.xml"
    source_strings, source_plurals, invariant, source_problems = parse_resources(source_path)
    for problem in source_problems:
        print(f"{source_path}: {problem}", file=sys.stderr)
    if source_problems:
        return 1

    # Only translatable source keys are required in a locale; invariant entries such as the
    # pure-format remote machine summary stay English by design.
    required = {name: text for name, text in source_strings.items() if name not in invariant}

    locales = dict(LOCALES)
    if args.locale:
        wanted = args.locale.lower()
        selected = {folder: tag for folder, tag in locales.items()
                    if folder.lower() == wanted or tag.lower() == wanted}
        if not selected:
            print(f"unknown locale '{args.locale}'", file=sys.stderr)
            return 1
        locales = selected

    report = {}
    failed = False
    for folder, tag in sorted(locales.items()):
        directory = args.res_dir / f"values-{folder}"
        result, problems = check_locale(tag, directory, source_strings, source_plurals, required)
        errors = [p for p in problems if p.severity == "error"]
        warnings = [p for p in problems if p.severity == "warning"]
        if errors or (args.strict and warnings):
            failed = True
        locale_strings = result["strings"] if result else {}
        translated = sum(1 for key in required if key in locale_strings)
        report[folder] = {
            "tag": tag,
            "translated": translated,
            "required": len(required),
            "coverage": round(100.0 * translated / len(required), 2) if required else 100.0,
            "errors": [p.as_dict() for p in errors],
            "warnings": [p.as_dict() for p in warnings],
        }

    hardcoded = []
    check_hardcoded_ui_text(args.kotlin_dir, hardcoded)
    for problem in hardcoded:
        print(f"{problem}", file=sys.stderr)
    if hardcoded:
        failed = True

    if args.format == "json":
        print(json.dumps(report, indent=2, ensure_ascii=False))
        return 1 if failed else 0

    print(f"source: {len(source_strings)} strings ({len(required)} translatable), "
          f"{len(source_plurals)} plurals, {len(invariant)} invariant")
    print()
    for folder, data in report.items():
        status = "FAIL" if data["errors"] else "ok  "
        print(f"{status} values-{folder:<7} {data['tag']:<6} "
              f"{data['translated']}/{data['required']} ({data['coverage']}%)")
        for error in data["errors"][:20]:
            print(f"       {error['severity']} {error['kind']} {error['key'] or ''}: {error['detail']}")
        if len(data["errors"]) > 20:
            print(f"       ... {len(data['errors']) - 20} more errors")
        for warning in data["warnings"][:10]:
            print(f"       {warning['severity']} {warning['kind']} {warning['key'] or ''}: {warning['detail']}")
        if len(data["warnings"]) > 10:
            print(f"       ... {len(data['warnings']) - 10} more warnings")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
