plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ing.offlineidv.storage"
}

dependencies {
    api(project(":android:core"))
}
