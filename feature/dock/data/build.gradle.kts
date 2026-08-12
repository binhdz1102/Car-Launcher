plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.feature.dock.data"
}

dependencies {
    implementation(project(":feature:dock:domain"))
    implementation(project(":libraries:dock"))
    implementation(libs.coroutines.android)
}
