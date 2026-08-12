plugins {
    id("launcher.android.library")
    id("launcher.android.hilt")
}

val platformArtifactsDirectory =
    rootProject.extensions.extraProperties["platformArtifactsDirectory"] as File

fun platformArtifact(name: String) = platformArtifactsDirectory.resolve(name)

android {
    namespace = "com.android.car.carlauncher.feature.launcher.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:platform"))
    implementation(project(":feature:launcher:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.timber)

    // Android Automotive exposes these APIs through the platform image, not the public SDK.
    compileOnly(files(platformArtifact("android.car.jar")))
    // ActivityTaskManager is a hidden platform API used by the privileged recents adapter.
    compileOnly(files(platformArtifact("framework.jar")))
    compileOnly(files(platformArtifact("systemui-shared.jar")))
    compileOnly(files(platformArtifact("systemui-shared-kotlin.jar")))
    compileOnly(files(platformArtifact("wm-shell-aidls.jar")))
    compileOnly(files(platformArtifact("wm-shell-shared.jar")))

    testImplementation(libs.junit)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(rootProject.tasks.named("verifyPlatformArtifacts"))
}
