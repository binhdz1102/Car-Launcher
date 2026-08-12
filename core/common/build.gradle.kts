plugins {
    id("launcher.android.library")
}

val platformArtifactsDirectory =
    rootProject.extensions.extraProperties["platformArtifactsDirectory"] as File

android {
    namespace = "com.android.car.carlauncher.core.common"
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.dagger.hilt.android)
    implementation(libs.timber)

    // Car is supplied by the target AAOS image. Keep it out of the standalone APK.
    compileOnly(files(platformArtifactsDirectory.resolve("android.car.jar")))
}
