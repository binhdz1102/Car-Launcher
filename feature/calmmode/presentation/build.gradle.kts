plugins {
    id("launcher.android.feature")
    id("launcher.android.hilt")
}

android {
    namespace = "com.android.car.carlauncher.feature.calmmode.presentation"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":feature:calmmode:data"))
    implementation(project(":feature:calmmode:domain"))
    implementation(project(":feature:media:domain"))
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.timber)
}
