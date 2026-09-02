plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ing.offlineidv.nfc"
}

dependencies {
    api(project(":android:core"))
    testImplementation(libs.junit4)
}
