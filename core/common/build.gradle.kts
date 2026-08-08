plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.core.common"
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.dagger.hilt.android)
    implementation(libs.timber)

    // Car is supplied by the target AAOS image. Keep it out of the standalone APK.
    compileOnly(files(rootProject.file("../My-System-App/libs/platform/android.car.jar")))
}
