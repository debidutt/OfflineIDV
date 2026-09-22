pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository {
                mavenCentral()
            }
            filter {
                includeGroup("org.jmrtd")
                includeGroup("net.sf.scuba")
                includeGroup("org.bouncycastle")
                includeGroup("org.ejbca.cvc")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "project-atlas"

include(
    ":android:accessibility",
    ":android:analytics",
    ":android:app-demo",
    ":android:camera",
    ":android:core",
    ":android:face",
    ":android:mrz",
    ":android:nfc",
    ":android:ocr",
    ":android:storage",
    ":android:ui",
    ":android:verification",
)
