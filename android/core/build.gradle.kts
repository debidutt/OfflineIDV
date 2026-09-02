plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ing.offlineidv.core"
}

dependencies {
    testImplementation(libs.junit4)
}
