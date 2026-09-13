// Top-level build file: declares plugin versions once so module build.gradle.kts
// files can `apply` them without re-specifying a version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // No org.jetbrains.kotlin.android here: AGP 9's built-in Kotlin support
    // compiles Kotlin in Android modules directly — applying it errors
    // ("no longer required for Kotlin support since AGP 9.0"). Pure-Kotlin
    // (non-Android) modules still need org.jetbrains.kotlin.jvm.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    // KSP: Room's annotation processor, applied where there are @Entity/@Dao
    // classes to generate code from — first needed by ticket 05's data module.
    alias(libs.plugins.ksp) apply false
}

// Every module (Android or pure-Kotlin) targets the same JVM bytecode level.
// Centralized here instead of repeating this block in all five module
// build.gradle.kts files.
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
