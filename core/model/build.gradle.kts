plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.core.model"
}

dependencies {
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
}
