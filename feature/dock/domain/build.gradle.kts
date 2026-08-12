plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.feature.dock.domain"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.coroutines.core)
}
