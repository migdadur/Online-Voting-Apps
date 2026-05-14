// build.gradle.kts (Project level)
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("com.google.gms.google-services") version "4.4.0" apply false
    // Add the Compose Compiler plugin
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
}