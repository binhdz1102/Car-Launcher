plugins {
    id("launcher.android.feature")
}

android {
    namespace = "com.android.car.carlauncher.feature.home.presentation"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":feature:home:domain"))
}
