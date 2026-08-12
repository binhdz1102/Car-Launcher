plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.feature.home.domain"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.coroutines.core)
}
