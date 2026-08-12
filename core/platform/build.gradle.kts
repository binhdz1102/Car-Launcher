plugins {
    id("launcher.android.library")
    id("launcher.android.hilt")
}

val platformArtifactsDirectory =
    rootProject.extensions.extraProperties["platformArtifactsDirectory"] as File

android {
    namespace = "com.android.car.carlauncher.core.platform"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.timber)

    // These are supplied by the API 37 AAOS image and verified before every app build.
    compileOnly(files(platformArtifactsDirectory.resolve("android.car.jar")))
    compileOnly(files(platformArtifactsDirectory.resolve("systemui-shared.jar")))
    compileOnly(files(platformArtifactsDirectory.resolve("wm-shell-shared.jar")))
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(rootProject.tasks.named("verifyPlatformArtifacts"))
}
