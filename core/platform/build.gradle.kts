plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.core.platform"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
}
