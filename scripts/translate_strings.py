#!/usr/bin/env python3
"""Fill missing Android string translations from the English strings.xml with googletrans.

Rules:
  * Source is app/src/main/res/values/strings.xml (English).
  * Only keys whose English text has MIN_WORDS (default 3) or more consecutive words are
    translated. A standalone "&" (or other symbol) breaks the run, so "Default & Tools" counts
    as 1 + 1 words. Shorter ones are left to the English fallback.
  * Keys that already exist in a language file are never touched or re-translated.
  * translatable="false" entries and entries with child XML tags are skipped.
  * Placeholders (%1$d, %s, %%, \\n, <b>...) are protected; a translation that loses
    one is rejected and not written.

Strings are sent in batches joined by a symbol-only separator to keep the number of requests low.

Setup:   pip install googletrans==4.0.2
Usage:   python3 scripts/translate_strings.py [--dry-run] [--langs de fr] [--min-words 3]
"""
import argparse
import asyncio
import inspect
import re
from importlib.metadata import PackageNotFoundError, version
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

RES_DIR = Path(__file__).resolve().parent.parent / "app/src/main/res"
LANG_DIR = re.compile(r"^values-([a-z]{2,3})(?:-r([A-Z]{2}))?$")
# Printf-style arguments, Android escapes and inline HTML tags must survive translation.
PROTECTED = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]|\\n|\\t|</?[a-zA-Z][^>]*>")


def googletrans_code(lang, region):
    if lang == "zh":
        return "zh-tw" if region == "TW" else "zh-cn"
    return lang


def word_run(text):
    """Longest run of consecutive words; a standalone symbol such as "&" ends the run."""
    plain = PROTECTED.sub(" ", text)
    best = run = 0
    for token in plain.split():
        if re.search(r"[^\W\d_]", token):
            run += 1
            best = max(best, run)
        else:
            run = 0
    return best


def read_source(path):
    """name -> text for translatable, tag-free <string> entries, in file order."""
    entries = {}
    for element in ET.parse(path).getroot().iter("string"):
        if element.get("translatable") == "false" or len(element):
            continue
        text = element.text or ""
        # Android escapes are not XML escapes: undo the ones re-applied on write.
        entries[element.get("name")] = text.replace("\\'", "'").replace('\\"', '"')
    return entries


def existing_keys(path):
    if not path.exists():
        return set()
    return {e.get("name") for e in ET.parse(path).getroot().iter() if e.get("name")}


def protect(text):
    tokens = []

    def swap(match):
        tokens.append(match.group(0))
        return f"XQ{len(tokens) - 1}X"

    return PROTECTED.sub(swap, text), tokens


def restore(text, tokens):
    for index, token in enumerate(tokens):
        if len(re.findall(rf"XQ{index}X", text)) != 1:
            return None
        text = re.sub(rf"XQ{index}X", lambda _m, t=token: t, text)
    return None if re.search(r"XQ\d+X", text) else text


def android_escape(text):
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("'", "\\'").replace('"', '\\"')
    if text.startswith(("@", "?")):
        text = "\\" + text
    return text


# Symbol-only line between strings: Google leaves it alone and does not merge the neighbours
# into one sentence. A batch whose separator count comes back wrong is split in half and retried.
SEPARATOR = "\n\n§§§\n\n"
SPLIT = re.compile(r"\s*§+\s*")


def chunk(items, max_items, max_chars):
    batch, size = [], 0
    for item in items:
        length = len(item[1]) + len(SEPARATOR)
        if batch and (len(batch) >= max_items or size + length > max_chars):
            yield batch
            batch, size = [], 0
        batch.append(item)
        size += length
    if batch:
        yield batch


def translate_batch(translate, texts, dest, delay):
    """One request for all texts when the result splits back cleanly, otherwise bisect."""
    if len(texts) == 1:
        result = translate(texts[0], dest)
        time.sleep(delay)
        return [result]
    result = translate(SEPARATOR.join(texts), dest)
    time.sleep(delay)
    parts = SPLIT.split(result.strip())
    if len(parts) == len(texts):
        return parts
    print(f"    batch of {len(texts)} came back as {len(parts)} parts, splitting", file=sys.stderr)
    middle = len(texts) // 2
    return (translate_batch(translate, texts[:middle], dest, delay) +
            translate_batch(translate, texts[middle:], dest, delay))


def make_translator():
    try:
        from googletrans import Translator
        installed_version = version("googletrans")
    except (ImportError, PackageNotFoundError):
        sys.exit("googletrans is missing: install googletrans==4.0.2")
    if installed_version != "4.0.2":
        sys.exit(
            f"googletrans==4.0.2 is required (found {installed_version}); "
            "install the documented version before translating."
        )

    def translate(text, dest):
        # googletrans 3.4.0 exposes an async translate() backed by a persistent
        # httpx.AsyncClient. Reusing that client across asyncio.run() calls closes
        # its event loop after the first request, causing later calls to fail with
        # "Event loop is closed". Use one client per request and close it inside
        # the same event loop that performs the request.
        for attempt in range(4):
            translator = Translator()
            try:
                result = translator.translate(text, src="en", dest=dest)
                if inspect.isawaitable(result):
                    async def await_translation():
                        try:
                            return await result
                        finally:
                            client = getattr(translator, "client", None)
                            close = getattr(client, "aclose", None)
                            if close is not None:
                                await close()
                    result = asyncio.run(await_translation())
                else:
                    return result.text
                return result.text
            except Exception as error:  # network hiccups / rate limits
                if attempt == 3:
                    raise
                time.sleep(1.5 * (attempt + 1))
                print(f"    retry after: {error}", file=sys.stderr)

    return translate


def append_entries(path, lines):
    if path.exists():
        content = path.read_text(encoding="utf-8")
        marker = content.rindex("</resources>")
        head = content[:marker].rstrip("\n") + "\n"
        path.write_text(head + "\n".join(lines) + "\n" + content[marker:], encoding="utf-8")
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + "\n".join(lines) + "\n</resources>\n",
            encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--res-dir", type=Path, default=RES_DIR)
    parser.add_argument("--langs", nargs="*", help="only these folders/codes, e.g. de zh-rTW (default: all)")
    parser.add_argument("--min-words", type=int, default=3, help="skip English texts without this many consecutive words (default 3)")
    parser.add_argument("--batch-size", type=int, default=25, help="strings per request (default 25)")
    parser.add_argument("--max-chars", type=int, default=3500, help="characters per request (default 3500)")
    parser.add_argument("--delay", type=float, default=1.0, help="seconds between requests (default 1.0)")
    parser.add_argument("--dry-run", action="store_true", help="list what would be translated; no network, no writes")
    args = parser.parse_args()

    source = read_source(args.res_dir / "values/strings.xml")
    eligible = {k: v for k, v in source.items() if word_run(v) >= args.min_words}
    print(f"{len(source)} translatable keys, {len(eligible)} with >= {args.min_words} consecutive words")

    wanted = {w.lower().removeprefix("values-") for w in args.langs or []}
    targets = []
    for folder in sorted(args.res_dir.iterdir()):
        match = LANG_DIR.match(folder.name)
        if not match or not folder.is_dir():
            continue
        lang, region = match.groups()
        if args.langs and folder.name.removeprefix("values-").lower() not in wanted and lang not in wanted:
            continue
        targets.append((folder / "strings.xml", googletrans_code(lang, region)))

    translate = None if args.dry_run else make_translator()
    for path, code in targets:
        have = existing_keys(path)
        missing = {k: v for k, v in eligible.items() if k not in have}
        print(f"{path.parent.name}: {len(missing)} missing")
        lines = []
        if args.dry_run:
            for key, text in missing.items():
                print(f"    would translate {key}: {text}")
        else:
            prepared = [(key, *protect(text)) for key, text in missing.items()]
            for batch in chunk([(k, m, t) for k, m, t in prepared], args.batch_size, args.max_chars):
                results = translate_batch(translate, [m for _, m, _ in batch], code, args.delay)
                for (key, _, tokens), translated in zip(batch, results):
                    translated = restore(translated, tokens)
                    if not translated or not translated.strip():
                        print(f"    skipped {key}: placeholders lost in translation", file=sys.stderr)
                        continue
                    lines.append(f'    <string name="{key}">{android_escape(translated.strip())}</string>')
        if lines:
            append_entries(path, lines)
            print(f"    wrote {len(lines)} strings")


if __name__ == "__main__":
    main()
