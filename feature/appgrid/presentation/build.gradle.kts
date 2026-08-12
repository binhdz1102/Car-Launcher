plugins {
    id("launcher.android.feature")
}

android {
    namespace = "com.android.car.carlauncher.feature.appgrid.presentation"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":feature:appgrid:domain"))
}
