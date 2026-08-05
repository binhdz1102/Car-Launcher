plugins {
    id("launcher.android.feature")
    id("launcher.android.hilt")
}

android {
    namespace = "com.android.car.carlauncher.feature.launcher.presentation"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":feature:launcher:domain"))
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.timber)

    compileOnly(files(rootProject.file("../My-System-App/libs/platform/android.car.jar")))

    testImplementation(libs.junit)
}
