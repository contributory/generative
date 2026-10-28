# PandaCI Android builds

PandaCI discovers `*.workflow.ts` in this directory when the repository is
connected to a PandaCI project.

- `debug.workflow.ts`: runs on each push; runs unit tests, lint, and assembles
  `app/build/outputs/apk/debug/Generative-debug.apk`.
- `release.workflow.ts`: runs **only on pushes to `ci/release`** (PandaCI does
  not currently document a manual trigger). This branch is intentionally
  separate from `main`, so `scripts/publish-github-release.sh` creates a
  pre-release tagged with the branch suffix, not a stable release.

Configure these PandaCI project secrets for the release workflow, restricting
access to the `ci/release` branch using a PandaCI environment:

- `KEYSTORE_BASE64`: Base64 of the Android signing keystore (single line).
- `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`: signing credentials.
- `GH_TOKEN` (or `GITHUB_TOKEN`): token with permission to manage the repo's
  GitHub releases and tags.

Optional release notes secrets: `RELEASE_NOTES_API_BASE_URL`,
`RELEASE_NOTES_API_KEY`, `RELEASE_NOTES_MODEL`.

PandaCI's documented workflow API does not currently describe a built-in
artifact-upload step. The debug APK is produced in the job workspace; the
release workflow publishes the signed APK and SHA-1 to GitHub Release using the
existing project script.
