plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.feature.home.data"
}

dependencies {
    implementation(project(":core:platform"))
    implementation(project(":feature:home:domain"))
    implementation(libs.coroutines.core)
}
