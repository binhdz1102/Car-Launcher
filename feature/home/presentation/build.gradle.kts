plugins {
    id("launcher.android.feature")
}

val platformArtifactsDirectory =
    rootProject.extensions.extraProperties["platformArtifactsDirectory"] as File

android {
    namespace = "com.android.car.carlauncher.feature.home.presentation"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:platform"))
    implementation(project(":core:ui"))
    implementation(project(":feature:home:domain"))
    implementation(libs.timber)

    compileOnly(files(platformArtifactsDirectory.resolve("android.car.jar")))
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(rootProject.tasks.named("verifyPlatformArtifacts"))
}
