plugins {
    id("launcher.android.library")
    id("launcher.android.hilt")
}

val platformArtifactsDirectory =
    rootProject.extensions.extraProperties["platformArtifactsDirectory"] as File

android {
    namespace = "com.android.car.carlauncher.feature.calmmode.data"
}

dependencies {
    implementation(project(":core:platform"))
    implementation(project(":feature:calmmode:domain"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.timber)
    compileOnly(files(platformArtifactsDirectory.resolve("android.car.jar")))
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(rootProject.tasks.named("verifyPlatformArtifacts"))
}
