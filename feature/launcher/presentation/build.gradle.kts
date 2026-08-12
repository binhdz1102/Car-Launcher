plugins {
    id("launcher.android.feature")
    id("launcher.android.hilt")
}

val platformArtifactsDirectory =
    rootProject.extensions.extraProperties["platformArtifactsDirectory"] as File

android {
    namespace = "com.android.car.carlauncher.feature.launcher.presentation"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:platform"))
    implementation(project(":core:ui"))
    implementation(project(":feature:launcher:domain"))
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.timber)

    compileOnly(files(platformArtifactsDirectory.resolve("android.car.jar")))

    testImplementation(libs.junit)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(rootProject.tasks.named("verifyPlatformArtifacts"))
}
