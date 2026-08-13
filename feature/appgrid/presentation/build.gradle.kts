plugins {
    id("launcher.android.feature")
    id("launcher.android.hilt")
}

android {
    namespace = "com.android.car.carlauncher.feature.appgrid.presentation"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":feature:appgrid:domain"))
    implementation(libs.timber)

    testImplementation(libs.junit)
}
