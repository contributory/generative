#!/usr/bin/env bash
set -euo pipefail

GITHUB_RELEASE_TOKEN="${GH_TOKEN:-${GITHUB_TOKEN:-}}"
: "${GITHUB_RELEASE_TOKEN:?Set secret GITHUB_TOKEN (or GH_TOKEN) in Codemagic environment group github_release}"
export GH_TOKEN="$GITHUB_RELEASE_TOKEN"
repo="${GITHUB_RELEASE_REPO:-starfall-org/generative}"
cd "${CM_BUILD_DIR:-${AC_REPOSITORY_DIR:-$(git rev-parse --show-toplevel)}}"
command -v gh >/dev/null || { echo 'GitHub CLI (gh) is required.' >&2; exit 1; }

# Keep the GitHub tag in sync with the version embedded in the APK.
metadata_json="${RELEASE_METADATA_JSON:-app/build/outputs/apk/release/output-metadata.json}"
version="$(python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))["elements"][0]["versionName"])' "$metadata_json")"

# Find the branch even for detached CI checkouts. The full branch name (including
# slashes) is part of the version on every branch except main.
branch="$(git branch --show-current 2>/dev/null || true)"
if [[ -z "$branch" ]]; then
  remote_branches="$(git for-each-ref --points-at HEAD --format='%(refname:strip=3)' refs/remotes/origin | grep -vx 'HEAD' || true)"
  if grep -qx 'main' <<<"$remote_branches"; then
    branch=main
  else
    branch="$(head -n 1 <<<"$remote_branches")"
  fi
fi
[[ -n "$branch" ]] || { echo 'Cannot determine the current git branch.' >&2; exit 1; }

if [[ ! "$version" =~ ^([0-9]+\.[0-9]+\.[0-9]+)(.*)$ ]]; then
  echo "Invalid app version: $version (expected <major>.<minor>.<commit-count>[-<branch-name>])." >&2
  exit 1
fi
base_version="${BASH_REMATCH[1]}"
if [[ "$branch" == main ]]; then
  is_prerelease=false
  expected_suffix=''
else
  is_prerelease=true
  expected_suffix="-$branch"
fi
expected_version="$base_version$expected_suffix"
[[ "$version" == "$expected_version" ]] || {
  echo "App version $version does not match branch $branch (expected $expected_version)." >&2
  exit 1
}

expected_tag="$version"
tag="${GITHUB_RELEASE_TAG:-$expected_tag}"
[[ "$tag" == "$expected_tag" ]] || { echo "Configured GitHub release tag $tag does not match app version $expected_tag." >&2; exit 1; }
if [[ -n "${CM_TAG:-}" && "$CM_TAG" != "$tag" ]]; then
  echo "Build tag $CM_TAG does not match configured release tag $tag." >&2
  exit 1
fi

echo "Branch: $branch; publishing as $([[ "$is_prerelease" == true ]] && echo pre-release || echo release) ($tag)."

commit="$(git rev-parse HEAD)"
short_commit="$(git rev-parse --short=12 HEAD)"
shopt -s nullglob
apk_dir="${RELEASE_APK_DIR:-app/build/outputs/apk/release}"
# Skip unsigned APKs (AGP convention: "*unsigned*.apk") — only signed builds are published.
apks=()
for candidate in "$apk_dir"/*.apk; do
  case "$(basename "$candidate")" in
    *unsigned*) continue ;;
  esac
  apks+=("$candidate")
done
(( ${#apks[@]} > 0 )) || { echo 'Missing signed release APKs.' >&2; exit 1; }
checksums=()
for apk in "${apks[@]}"; do
  checksum="$apk.sha1"
  hash="$(shasum -a 1 "$apk" | awk '{print $1}')"
  printf '%s  %s\n' "$hash" "$(basename "$apk")" > "$checksum"
  checksums+=("$checksum")
done

if gh api "repos/$repo/git/ref/tags/$tag" >/dev/null 2>&1; then
  gh api --method PATCH "repos/$repo/git/refs/tags/$tag" \
    -f sha="$commit" -F force=true >/dev/null
else
  gh api --method POST "repos/$repo/git/refs" \
    -f ref="refs/tags/$tag" -f sha="$commit" >/dev/null
fi

if gh release view "$tag" --repo "$repo" >/dev/null 2>&1; then
  echo "Release $tag already exists; tag moved to $short_commit and matching artifacts will be replaced."
else
  create_flags=(--latest)
  [[ "$is_prerelease" != true ]] || create_flags=(--prerelease --latest=false)
  gh release create "$tag" --repo "$repo" \
    --title "v$tag" --generate-notes "${create_flags[@]}"
fi

# Prefix GitHub's generated changelog with release metadata that makes the
# downloadable files and verification method obvious at a glance.
release_body="$(gh release view "$tag" --repo "$repo" --json body --jq '.body // ""')"
release_marker='<!-- generative-release-info -->'
ai_notes=""
ai_base="${RELEASE_NOTES_API_BASE_URL:-${AI_BASE_URL:-}}"
ai_key="${RELEASE_NOTES_API_KEY:-${AI_API_KEY:-}}"
ai_model="${RELEASE_NOTES_MODEL:-${AI_MODEL:-gpt-4o-mini}}"

# Keep a previously generated note as a fallback, but always retry AI generation
# on every publish. Older versions skipped this whole block once the marker existed.
if [[ "$release_body" == *"$release_marker"* ]]; then
  previous_notes="$(printf '%s\n' "$release_body" | sed -n '/^### What/,$p' | tail -n +3)"
  if [[ -n "$previous_notes" ]]; then
    release_body="$previous_notes"
  else
    release_body=""
  fi
fi

if [[ -n "$ai_base" ]]; then
  echo "Generating AI release notes (model: $ai_model)."
  ai_tmp="$(mktemp -d)"
  previous_tag="$(git describe --tags --abbrev=0 "$commit^" 2>/dev/null || true)"
  if [[ -n "$previous_tag" ]]; then
    commit_messages="$(git log --no-merges --format='%s' "$previous_tag..$commit" 2>/dev/null || true)"
  else
    commit_messages="$(git log -20 --no-merges --format='%s' "$commit" 2>/dev/null || true)"
  fi
  printf '%s' "$commit_messages" > "$ai_tmp/commit-messages.txt"
  python3 - "$ai_model" "$ai_tmp/commit-messages.txt" "$ai_tmp/request.json" <<'PY_REQUEST'
import json, sys
model, messages_path, request_path = sys.argv[1:]
commit_messages = open(messages_path, encoding="utf-8").read().strip()
payload = {"model": model, "temperature": 0.2, "messages": [
    {"role": "system", "content": "Write concise English release notes for end users of the Generative Android app using only the supplied commit messages. Describe likely user-visible features, usability improvements, and fixes. Do not mention internal code, refactoring, tests, CI, commit history, or developer tasks. Do not invent details not supported by the messages. Return 2-6 short Markdown bullets only."},
    {"role": "user", "content": "Commit messages for this release:\n" + (commit_messages or "No commit messages available; provide one conservative general maintenance note.")},
]}
with open(request_path, "w", encoding="utf-8") as stream:
    json.dump(payload, stream)
PY_REQUEST
  ai_base="${ai_base%/}"
  [[ "$ai_base" == */chat/completions ]] || ai_base="$ai_base/chat/completions"
  curl_args=(--silent --show-error --connect-timeout "${RELEASE_NOTES_CONNECT_TIMEOUT_SECONDS:-5}" --max-time "${RELEASE_NOTES_TIMEOUT_SECONDS:-20}" -H 'Content-Type: application/json' --data-binary "@$ai_tmp/request.json" -o "$ai_tmp/response.json" -w '%{http_code}')
  [[ -z "$ai_key" ]] || curl_args+=(-H "Authorization: Bearer $ai_key")
  printf "AI release-note endpoint: %s\n" "$(printf '%s' "$ai_base" | sed 's/[?#].*$//')"
  if http_status="$(curl "${curl_args[@]}" "$ai_base")"; then
    if [[ "$http_status" =~ ^2[0-9][0-9]$ ]]; then
      if ! ai_notes="$(python3 - "$ai_tmp/response.json" <<'PY_RESPONSE'
import json, sys
try:
    with open(sys.argv[1], encoding="utf-8") as stream:
        data = json.load(stream)
    content = data["choices"][0]["message"]["content"]
    if not isinstance(content, str) or len(content.strip()) < 20:
        raise ValueError("response choices[0].message.content is missing or too short")
    print(content.strip())
except (OSError, ValueError, KeyError, IndexError, TypeError) as exc:
    print(f"Could not parse AI response: {exc}", file=sys.stderr)
    sys.exit(1)
PY_RESPONSE
      )"; then
        echo 'Warning: AI API returned HTTP success but no usable chat-completion content; using fallback notes.' >&2
      else
        echo 'AI release notes generated successfully.'
      fi
    else
      echo "Warning: AI release-note API returned HTTP ${http_status:-unknown}; response body follows (secrets/authorization headers are not printed):" >&2
      python3 - "$ai_tmp/response.json" <<'PY_ERROR'
import sys
try:
    body = open(sys.argv[1], encoding="utf-8").read().strip()
    print(body[:4000] if body else "<empty response body>", file=sys.stderr)
except OSError as exc:
    print(f"Could not read response body: {exc}", file=sys.stderr)
PY_ERROR
    fi
  else
    curl_status=$?
    echo "Warning: AI release-note request failed (curl exit ${curl_status}, HTTP ${http_status:-unknown}); continuing with fallback notes." >&2
    if [[ -s "$ai_tmp/response.json" ]]; then
      head -c 4000 "$ai_tmp/response.json" >&2
      printf '\n' >&2
    fi
  fi
  rm -rf "$ai_tmp"
else
  echo 'Warning: AI_BASE_URL/RELEASE_NOTES_API_BASE_URL is empty; skipping AI release notes and using fallback.' >&2
fi

if [[ -z "$ai_notes" ]]; then
  echo 'Warning: AI release notes were not generated; falling back to previous release notes or GitHub-generated notes.' >&2
  ai_notes="${release_body:-'- Includes general improvements and fixes for a smoother, more reliable app experience.'}"
fi
notes_file="$(mktemp)"
trap 'rm -f "$notes_file"' EXIT

{
  printf '%s\n' "$release_marker"
  printf '## Generative %s\n\n' "$tag"
  printf 'Android release build for **Generative %s**.\n\n' "$version"
  printf -- '- **Version:** `%s`\n' "$version"
  printf -- '- **Git tag:** `%s`\n' "$tag"
  printf -- '- **Channel:** %s\n' "$([[ "$is_prerelease" == true ]] && echo "Pre-release (branch \`$branch\`)" || echo 'Stable release')"
  printf -- '- **Commit:** `%s`\n\n' "$short_commit"

  printf '### Downloads\n\n'
  for apk in "${apks[@]}"; do
    printf -- '- `%s`\n' "$(basename "$apk")"
    printf -- '  - SHA-1 checksum: `%s.sha1`\n' "$(basename "$apk")"
  done

  printf '\n### Verify a downloaded APK\n\n'
  printf 'Run `shasum -a 1 <apk-file>` and compare the result with the matching `.sha1` file attached to this release.\n'
  printf '\n### What changed\n\n%s\n' "$ai_notes"
} > "$notes_file"

gh release edit "$tag" --repo "$repo" --title "v$tag" --notes-file "$notes_file"

gh release upload "$tag" "${apks[@]}" "${checksums[@]}" --repo "$repo" --clobber
if [[ "$is_prerelease" == true ]]; then
  gh release edit "$tag" --repo "$repo" --draft=false --prerelease --latest=false
else
  gh release edit "$tag" --repo "$repo" --draft=false --prerelease=false --latest
fi
gh release view "$tag" --repo "$repo" --json url --jq .url
