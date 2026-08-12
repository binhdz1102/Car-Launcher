plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.feature.recents.data"
}

dependencies {
    implementation(project(":core:platform"))
    implementation(project(":feature:recents:domain"))
    implementation(libs.coroutines.android)
}
