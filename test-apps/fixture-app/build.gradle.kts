plugins {
    id("launcher.android.application")
}

android {
    namespace = "com.android.car.carlauncher.fixture"

    defaultConfig {
        applicationId = "com.android.car.carlauncher.fixture"
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    testImplementation(libs.junit)
}
