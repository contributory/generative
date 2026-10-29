buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.12")
    }
}

plugins {
    id("com.android.application") version "9.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// The unqualified `./gradlew assembleDebug` command is the legacy build entry point.
// Keep the new app buildable explicitly via `./gradlew :app:assembleDebug`.
subprojects {
    tasks.configureEach {
        if (
            name == "assembleDebug" &&
            project.name == "app" &&
            gradle.startParameter.taskNames.any { it == "assembleDebug" }
        ) {
            enabled = false
        }
    }
}
