plugins {
    id("launcher.android.library")
    id("launcher.android.hilt")
}

android {
    namespace = "com.android.car.carlauncher.feature.launcher.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":feature:launcher:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.timber)

    // Android Automotive exposes these APIs through the platform image, not the public SDK.
    compileOnly(files(rootProject.file("../My-System-App/libs/platform/android.car.jar")))

    testImplementation(libs.junit)
}
