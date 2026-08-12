plugins {
    id("launcher.android.feature")
}

android {
    namespace = "com.android.car.carlauncher.feature.media.presentation"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":feature:media:domain"))
}
