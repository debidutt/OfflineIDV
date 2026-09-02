plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.ing.offlineidv.demo"

    buildFeatures {
        compose = true
    }

    defaultConfig {
        applicationId = "com.ing.offlineidv.demo"
        versionCode = 1
        versionName = "0.7.0-milestone7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":android:camera"))
    implementation(project(":android:ocr"))
    implementation(project(":android:nfc"))
    implementation(project(":android:ui"))
    implementation(project(":android:verification"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel)

    testImplementation(libs.junit4)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
