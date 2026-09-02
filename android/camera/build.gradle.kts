plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ing.offlineidv.camera"
}

dependencies {
    api(project(":android:core"))
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    testImplementation(libs.junit4)
}
