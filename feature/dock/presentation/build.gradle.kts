plugins {
    id("launcher.android.feature")
}

android {
    namespace = "com.android.car.carlauncher.feature.dock.presentation"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":feature:dock:domain"))
}
