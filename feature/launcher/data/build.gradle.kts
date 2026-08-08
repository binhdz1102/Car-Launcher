plugins {
    id("launcher.android.library")
    id("launcher.android.hilt")
}

val systemUiSharedLibJar =
    rootProject.file(
        "../../out-avd-car-mysystemapp/soong/.intermediates/frameworks/base/packages/SystemUI/shared/SystemUISharedLib/android_common/javac/SystemUISharedLib.jar",
    )
val systemUiSharedLibKotlinJar =
    rootProject.file(
        "../../out-avd-car-mysystemapp/soong/.intermediates/frameworks/base/packages/SystemUI/shared/SystemUISharedLib/android_common/kotlin/SystemUISharedLib.jar",
    )
val windowManagerShellAidlJar =
    rootProject.file(
        "../../out-avd-car-mysystemapp/soong/.intermediates/frameworks/base/libs/WindowManager/Shell/WindowManager-Shell-aidls/android_common/javac/WindowManager-Shell-aidls.jar",
    )
val windowManagerShellSharedJar =
    rootProject.file(
        "../../out-avd-car-mysystemapp/soong/.intermediates/frameworks/base/libs/WindowManager/Shell/shared/WindowManager-Shell-shared/android_common/javac/WindowManager-Shell-shared.jar",
    )

android {
    namespace = "com.android.car.carlauncher.feature.launcher.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":feature:launcher:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.timber)

    // Android Automotive exposes these APIs through the platform image, not the public SDK.
    compileOnly(files(rootProject.file("../My-System-App/libs/platform/android.car.jar")))
    // ActivityTaskManager is a hidden platform API used by the privileged recents adapter.
    compileOnly(files(rootProject.file("../My-System-App/libs/platform/framework.jar")))
    compileOnly(files(systemUiSharedLibJar))
    compileOnly(files(systemUiSharedLibKotlinJar))
    compileOnly(files(windowManagerShellAidlJar))
    compileOnly(files(windowManagerShellSharedJar))

    testImplementation(libs.junit)
}
