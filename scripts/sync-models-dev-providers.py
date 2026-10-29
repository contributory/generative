#!/usr/bin/env python3
"""Build the public provider directory from Models.dev's provider API."""
import json
import os
import re
import sys
import tempfile
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

SOURCE = "https://models.dev/api.json?type=all"
DEST = Path(__file__).resolve().parents[1] / "docs" / "providers-data.json"

# Models.dev intentionally omits some first-party/native SDK base URLs (shown as "-"
# in its provider table). For these well-known services, provide the documented
# endpoint and a protocol that Generative can actually open from a deep link.
# Names, documentation URLs, model counts, and the rest of the catalog still come
# from Models.dev.
NATIVE_ENDPOINTS = {
    "openai": ("https://api.openai.com/v1", "responses"),
    "anthropic": ("https://api.anthropic.com/v1", "anthropic"),
    "google": ("https://generativelanguage.googleapis.com/v1beta", "gemini"),
    "groq": ("https://api.groq.com/openai/v1", "chat-completions"),
    "mistral": ("https://api.mistral.ai/v1", "chat-completions"),
    "togetherai": ("https://api.together.ai/v1", "chat-completions"),
    "cerebras": ("https://api.cerebras.ai/v1", "chat-completions"),
    "xai": ("https://api.x.ai/v1", "chat-completions"),
    "fireworks-ai": ("https://api.fireworks.ai/inference/v1", "chat-completions"),
    "perplexity": ("https://api.perplexity.ai", "chat-completions"),
}

def valid_catalog(data):
    """Only use a complete, nonempty provider directory as the local fallback."""
    return (
        isinstance(data, dict)
        and isinstance(data.get("providers"), list)
        and len(data["providers"]) > 0
        and data.get("count") == len(data["providers"])
        and all(
            isinstance(item, dict)
            and isinstance(item.get("id"), str)
            and isinstance(item.get("name"), str)
            for item in data["providers"]
        )
    )


def fetch_catalog():
    request = urllib.request.Request(SOURCE, headers={"User-Agent": "Generative-provider-directory/1.0"})
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)


def build_catalog(raw):
    if not isinstance(raw, dict) or len(raw) < 10:
        raise ValueError("Models.dev returned an empty or incomplete provider catalog")
    if any(not isinstance(provider_id, str) or not isinstance(item, dict) for provider_id, item in raw.items()):
        raise ValueError("Models.dev returned unexpected provider data")
    providers = []
    for provider_id, item in sorted(raw.items(), key=lambda pair: (pair[1].get("name") or pair[0]).casefold()):
        api = (item.get("api") or "").strip()
        npm = (item.get("npm") or "").strip()
        if not api and provider_id in NATIVE_ENDPOINTS:
            api = NATIVE_ENDPOINTS[provider_id][0]
        models = item.get("models") or {}
        # Models.dev lists endpoint templates too. Do not pass templates containing
        # environment variables into an Android deep link as if they were usable URLs.
        concrete_api = bool(
            api
            and re.match(r"^https?://[^\s]+$", api)
            and not re.search(r"\$\{|\{[A-Z_]+\}", api)
            and not re.search(r"https?://(?:localhost|127\.0\.0\.1|0\.0\.0\.0)(?::|/|$)", api, re.I)
        )
        if provider_id in NATIVE_ENDPOINTS:
            app_type = NATIVE_ENDPOINTS[provider_id][1]
            type_label = {"responses": "OpenAI Responses", "anthropic": "Anthropic Messages", "gemini": "Google Gemini API"}.get(app_type, "OpenAI-compatible API")
            supported = True
        elif npm == "@ai-sdk/anthropic":
            app_type, type_label = "anthropic", "Anthropic Messages"
            supported = True
        elif npm == "@ai-sdk/google":
            app_type, type_label = "gemini", "Google Gemini API"
            supported = True
        elif npm in ("@ai-sdk/openai-compatible", "@ai-sdk/openai"):
            app_type, type_label = "chat-completions", "OpenAI-compatible API"
            supported = True
        else:
            app_type, type_label = "chat-completions", (npm or "API details")
            supported = False
        can_add = concrete_api and supported
        providers.append({
            "id": provider_id,
            "name": item.get("name") or provider_id,
            "api": api if concrete_api else "",
            "docs": item.get("doc") or "",
            "npm": npm,
            "modelCount": len(models),
            "logo": f"https://models.dev/logos/{provider_id}.svg",
            "appType": app_type,
            "typeLabel": type_label,
            "canAdd": can_add,
        })

    payload = {
        "source": SOURCE,
        "sourcePage": "https://models.dev/providers/",
        "updatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "count": len(providers),
        "providers": providers,
    }
    if not valid_catalog(payload):
        raise ValueError("Generated provider catalog is invalid")
    return payload


def write_catalog(payload):
    """Replace the cache only after a complete catalog was generated."""
    temporary = None
    try:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=DEST.parent, prefix=".providers-", suffix=".tmp", delete=False) as output:
            temporary = Path(output.name)
            json.dump(payload, output, ensure_ascii=False, separators=(",", ":"))
            output.write("\n")
        temporary.chmod(0o644)  # Public GitHub Pages asset, not a private cache.
        os.replace(temporary, DEST)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main():
    try:
        payload = build_catalog(fetch_catalog())
        write_catalog(payload)
    except (OSError, ValueError, TypeError, KeyError) as error:
        # docs/providers-data.json is committed to the repository and remains
        # available even when the external API is down or changes its format.
        try:
            cached = json.loads(DEST.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            cached = None
        if valid_catalog(cached):
            print(f"::warning::Provider catalog refresh failed ({error}); publishing the cached catalog instead.")
            print(f"Keeping {cached['count']} cached providers in {DEST}")
            return 0
        print(f"::warning::Provider catalog refresh failed ({error}); no usable cached catalog is available.")
        return 1
    print(f"Wrote {payload['count']} providers ({sum(p['canAdd'] for p in payload['providers'])} with a supported one-tap app link) to {DEST}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
