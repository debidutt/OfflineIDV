plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ing.offlineidv.accessibility"
}

dependencies {
    api(project(":android:core"))
}
