plugins {
    id("launcher.android.library")
    id("launcher.android.hilt")
}

val platformArtifactsDirectory =
    rootProject.extensions.extraProperties["platformArtifactsDirectory"] as File

android {
    namespace = "com.android.car.carlauncher.feature.media.data"
}

dependencies {
    implementation(project(":core:platform"))
    implementation(project(":feature:media:domain"))
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.timber)

    // Media and projection managers are supplied by the matching API 37 AAOS image.
    compileOnly(files(platformArtifactsDirectory.resolve("android.car.jar")))

    testImplementation(libs.junit)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(rootProject.tasks.named("verifyPlatformArtifacts"))
}
