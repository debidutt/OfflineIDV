plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ing.offlineidv.mrz"
}

dependencies {
    api(project(":android:core"))
    testImplementation(libs.junit4)
}
