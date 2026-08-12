plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.feature.media.data"
}

dependencies {
    implementation(project(":feature:media:domain"))
    implementation(libs.coroutines.android)
}
