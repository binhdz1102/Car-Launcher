plugins {
    id("launcher.android.library")
    id("launcher.android.hilt")
}

val platformArtifactsDirectory =
    rootProject.extensions.extraProperties["platformArtifactsDirectory"] as File

android {
    namespace = "com.android.car.carlauncher.feature.recents.data"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:platform"))
    implementation(project(":feature:recents:domain"))
    implementation(libs.coroutines.android)
    implementation(libs.timber)

    compileOnly(files(platformArtifactsDirectory.resolve("android.car.jar")))
    compileOnly(files(platformArtifactsDirectory.resolve("framework.jar")))
    compileOnly(files(platformArtifactsDirectory.resolve("systemui-shared.jar")))
    compileOnly(files(platformArtifactsDirectory.resolve("systemui-shared-kotlin.jar")))
    compileOnly(files(platformArtifactsDirectory.resolve("wm-shell-aidls.jar")))
    compileOnly(files(platformArtifactsDirectory.resolve("wm-shell-shared.jar")))

    testImplementation(libs.junit)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(rootProject.tasks.named("verifyPlatformArtifacts"))
}
