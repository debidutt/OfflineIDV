plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ing.offlineidv.ocr"
}

dependencies {
    api(project(":android:core"))
    implementation(libs.mlkit.text.recognition)
    testImplementation(libs.junit4)
}
