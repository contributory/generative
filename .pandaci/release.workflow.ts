import { $, docker, env, type Config } from "jsr:@pandaci/workflow";

// PandaCI currently offers push/PR triggers, not a manual release trigger.
// This dedicated branch requires an intentional push and produces a prerelease.
export const config: Config = {
  name: "Generative Android Release",
  on: { push: { branches: ["ci/release"] } },
};

await docker("ghcr.io/cirruslabs/android-sdk:36", async () => {
  await $`bash .pandaci/build.sh release`.env({
    CI_BRANCH: env.PANDACI_BRANCH,
    KEYSTORE_BASE64: env.KEYSTORE_BASE64 ?? "",
    KEYSTORE_PASSWORD: env.KEYSTORE_PASSWORD ?? "",
    KEY_ALIAS: env.KEY_ALIAS ?? "",
    KEY_PASSWORD: env.KEY_PASSWORD ?? "",
    GH_TOKEN: env.GH_TOKEN ?? env.GITHUB_TOKEN ?? "",
    GITHUB_RELEASE_REPO: "starfall-org/generative",
    RELEASE_NOTES_API_BASE_URL: env.RELEASE_NOTES_API_BASE_URL ?? "",
    RELEASE_NOTES_API_KEY: env.RELEASE_NOTES_API_KEY ?? "",
    RELEASE_NOTES_MODEL: env.RELEASE_NOTES_MODEL || "gpt-4o-mini",
  });
});
