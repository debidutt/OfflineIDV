plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ing.offlineidv.face"
}

dependencies {
    api(project(":android:core"))
    testImplementation(libs.junit4)
}
