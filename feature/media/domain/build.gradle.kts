plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.feature.media.domain"
}

dependencies {
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
}
