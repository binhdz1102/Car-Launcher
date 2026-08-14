plugins {
    id("launcher.android.application")
}

android {
    namespace = "com.android.car.docklib.sample"

    defaultConfig {
        applicationId = "com.android.car.docklib.sample"
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(project(":libraries:dock"))
    implementation(libs.androidx.recyclerview)
    testImplementation(libs.junit)
}
