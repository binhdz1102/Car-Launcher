plugins {
    id("launcher.android.library")
    id("launcher.android.hilt")
}

val platformArtifactsDirectory =
    rootProject.extensions.extraProperties["platformArtifactsDirectory"] as File

android {
    namespace = "com.android.car.carlauncher.feature.home.data"
}

dependencies {
    implementation(project(":core:platform"))
    implementation(project(":feature:home:domain"))
    implementation(libs.coroutines.core)
    implementation(libs.timber)

    compileOnly(files(platformArtifactsDirectory.resolve("android.car.jar")))
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(rootProject.tasks.named("verifyPlatformArtifacts"))
}
