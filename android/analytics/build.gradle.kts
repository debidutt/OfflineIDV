plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ing.offlineidv.analytics"
}

dependencies {
    api(project(":android:core"))
}
