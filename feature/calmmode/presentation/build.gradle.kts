plugins {
    id("launcher.android.feature")
}

android {
    namespace = "com.android.car.carlauncher.feature.calmmode.presentation"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":feature:calmmode:domain"))
}
