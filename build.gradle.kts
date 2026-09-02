import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("android/**/src/**/*.kt")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "android/**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("projectFiles") {
        target(
            ".editorconfig",
            ".gitignore",
            "*.md",
            "*.properties",
            "android/**/src/**/*.xml",
            "docs/**/*.md",
            "ios/**/*.md",
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<ApplicationExtension> {
            compileSdk = 35

            defaultConfig {
                minSdk = 26
                targetSdk = 35
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            lint {
                abortOnError = true
                checkDependencies = true
                // Tool versions are intentionally pinned and upgraded only in a reviewed maintenance change.
                disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
                warningsAsErrors = true
            }
        }
    }

    pluginManager.withPlugin("com.android.library") {
        extensions.configure<LibraryExtension> {
            compileSdk = 35

            defaultConfig {
                minSdk = 26
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            lint {
                abortOnError = true
                // Tool versions are intentionally pinned and upgraded only in a reviewed maintenance change.
                disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
                warningsAsErrors = true
            }
        }
    }
}
