plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ing.offlineidv.nfc"
}

configurations.configureEach {
    resolutionStrategy.failOnVersionConflict()
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    api(project(":android:core"))
    implementation(libs.jmrtd) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
        exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
    }
    implementation(libs.scuba.smartcards)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.bouncycastle.bcutil) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
    }
    implementation(libs.ejbca.cert.cvc) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    }
    testImplementation(libs.junit4)
}
