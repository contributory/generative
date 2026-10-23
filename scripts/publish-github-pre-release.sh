#!/usr/bin/env bash
set -euo pipefail

GITHUB_RELEASE_TOKEN="${GH_TOKEN:-${GITHUB_TOKEN:-}}"
: "${GITHUB_RELEASE_TOKEN:?Set secret GITHUB_TOKEN (or GH_TOKEN) in Codemagic environment group github_release}"
export GH_TOKEN="$GITHUB_RELEASE_TOKEN"
repo="${GITHUB_RELEASE_REPO:-starfall-org/generative}"
cd "${CM_BUILD_DIR:-${AC_REPOSITORY_DIR:-$(git rev-parse --show-toplevel)}}"
command -v gh >/dev/null || { echo 'GitHub CLI (gh) is required.' >&2; exit 1; }

# Build one signed APK; main uses the unstable suffix, while other branches keep their full branch suffix.
"$PWD/scripts/build-unstable-apk.sh"

# Keep the GitHub prerelease tag in sync with both the APK version and branch.
metadata_json="${RELEASE_METADATA_JSON:-app/build/outputs/apk/release/output-metadata.json}"
version="$(python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))["elements"][0]["versionName"])' "$metadata_json")"

# Match Gradle's branch detection, including detached CI checkouts, and prefer main
# when several remote branches point at the same commit.
branch="$(git branch --show-current 2>/dev/null || true)"
if [[ -z "$branch" ]]; then
  remote_branches="$(git for-each-ref --points-at HEAD --format='%(refname:strip=3)' refs/remotes/origin | grep -vx 'HEAD' || true)"
  if grep -qx 'main' <<<"$remote_branches"; then
    branch=main
  else
    branch="$(head -n 1 <<<"$remote_branches")"
  fi
fi

if [[ ! "$version" =~ ^([0-9]+\.[0-9]+\.[0-9]+)(.*)$ ]]; then
  echo "Invalid pre-release app version: $version (expected <major>.<minor>.<patch>[-<branch-name>])." >&2
  exit 1
fi
base_version="${BASH_REMATCH[1]}"
if [[ -z "$branch" || "$branch" == main || "$branch" == HEAD ]]; then
  expected_version="$base_version-unstable"
else
  expected_version="$base_version-$branch"
fi
# Current Generative tags use the versionName verbatim, without a leading "v".
expected_tag="$expected_version"
[[ "$version" == "$expected_version" ]] || {
  echo "App version $version does not match branch ${branch:-<unknown>} (expected $expected_version)." >&2
  exit 1
}
tag="${GITHUB_RELEASE_TAG:-$expected_tag}"
[[ "$tag" == "$expected_tag" ]] || { echo "Configured GitHub prerelease tag $tag does not match expected tag $expected_tag." >&2; exit 1; }
if [[ -n "${CM_TAG:-}" && "$CM_TAG" != "$tag" ]]; then
  echo "Build tag $CM_TAG does not match configured prerelease tag $tag." >&2
  exit 1
fi
echo "Publishing pre-release ($tag)."

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
(( ${#apks[@]} == 1 )) || { echo "Expected exactly one signed APK, found ${#apks[@]}." >&2; exit 1; }
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

# Keep GitHub's "What's Changed" entries and Full Changelog comparison link.
# Omit release metadata, download/checksum instructions, and unrelated generated sections.
notes_file="$(mktemp)"
generated_notes_file="$(mktemp)"
trap 'rm -f "$notes_file" "$generated_notes_file"' EXIT

gh api --method POST "repos/$repo/releases/generate-notes" \
  -f tag_name="$tag" -f target_commitish="$commit" --jq '.body // ""' > "$generated_notes_file"
python3 - "$generated_notes_file" "$notes_file" <<'PY_NOTES'
import re
import sys
from pathlib import Path

body = Path(sys.argv[1]).read_text(encoding="utf-8").strip()
heading = re.search(r"(?im)^##\s+What's Changed\s*$", body)
if heading is None:
    raise SystemExit("GitHub-generated release notes do not contain a What's Changed section; refusing to publish them.")

content = body[heading.end():]
full_changelog = re.search(r"(?m)^\*\*Full Changelog\*\*:.+$", body)
next_section = re.search(r"(?m)^##\s+", content)
if next_section is not None:
    content = content[:next_section.start()]
full_changelog_text = full_changelog.group(0) if full_changelog else None

notes = body[heading.start():heading.end()] + content.rstrip()
if full_changelog_text:
    notes = notes.rstrip() + "\n\n" + full_changelog_text
Path(sys.argv[2]).write_text(notes.rstrip() + "\n", encoding="utf-8")
PY_NOTES

if gh release view "$tag" --repo "$repo" >/dev/null 2>&1; then
  echo "Release $tag already exists; tag moved to $short_commit and matching artifacts will be replaced."
  gh release edit "$tag" --repo "$repo" --title "v$tag" --notes-file "$notes_file"
else
  gh release create "$tag" --repo "$repo" \
    --title "v$tag" --notes-file "$notes_file" --prerelease --latest=false
fi

gh release upload "$tag" "${apks[@]}" "${checksums[@]}" --repo "$repo" --clobber
gh release edit "$tag" --repo "$repo" --draft=false --prerelease --latest=false
gh release view "$tag" --repo "$repo" --json url --jq .url
