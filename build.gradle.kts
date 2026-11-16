plugins {
    // AGP 9 bringt Kotlin (2.2.10) bereits mit; der Compose-Compiler muss dazu passen.
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
