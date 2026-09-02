plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ing.offlineidv.verification"
}

dependencies {
    api(project(":android:analytics"))
    api(project(":android:camera"))
    api(project(":android:core"))
    api(project(":android:face"))
    api(project(":android:mrz"))
    api(project(":android:nfc"))
    api(project(":android:ocr"))
    api(project(":android:storage"))
    testImplementation(libs.junit4)
}
