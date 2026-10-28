import { $, docker, type Config } from "jsr:@pandaci/workflow";

export const config: Config = {
  name: "Generative Android Debug",
  on: { push: { branches: ["*"] } },
};

// The image includes JDK 21 and Android SDK tools; install the app's SDK 37 below.
await docker("ghcr.io/cirruslabs/android-sdk:36", async () => {
  await $`bash .pandaci/build.sh debug`;
});
