import java.util.Properties

plugins {
    id("launcher.android.application")
    id("launcher.android.hilt")
}

val platformFrameworkJar = rootProject.file("../My-System-App/libs/platform/framework.jar")
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
val carQcLibJar =
    rootProject.file(
        "../../out-avd-car-mysystemapp/soong/.intermediates/packages/apps/Car/systemlibs/car-qc-lib/car-qc-lib/android_common/javac/car-qc-lib.jar",
    )

// These standalone projects are intentionally distributable without a .git
// directory. Keep the field available without making Gradle depend on git.
val gitCommitHash = "standalone"

val platformKeystoreProperties =
    Properties().apply {
        rootProject.file("keystore.properties").inputStream().use(::load)
    }

android {
    namespace = "com.android.car.carlauncher"

    defaultConfig {
        applicationId = "com.android.car.carlauncher"
        // Higher than the Baklava image's versionCode (37), so adb install -r
        // can test this development replacement without -d.
        versionCode = 1000
        versionName = "custom-dev"
        buildConfigField("String", "GIT_COMMIT_HASH", "\"$gitCommitHash\"")
    }

    signingConfigs {
        create("platform") {
            storeFile =
                rootProject.file(
                    platformKeystoreProperties.getProperty("storeFile"),
                )
            storePassword = platformKeystoreProperties.getProperty("storePassword")
            keyAlias = platformKeystoreProperties.getProperty("keyAlias")
            keyPassword = platformKeystoreProperties.getProperty("keyPassword")
            storeType = platformKeystoreProperties.getProperty("storeType")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("platform")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("platform")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":feature:launcher:data"))
    implementation(project(":feature:launcher:domain"))
    implementation(project(":feature:launcher:presentation"))

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.timber)

    // QuickStep's binder contract is a platform shared library in the AOSP build. It is
    // packaged into this standalone APK just as CarLauncher-core does in Soong.
    implementation(files(systemUiSharedLibJar))
    implementation(files(systemUiSharedLibKotlinJar))
    implementation(files(windowManagerShellAidlJar))
    implementation(files(windowManagerShellSharedJar))
    implementation(files(carQcLibJar))
    compileOnly(files(platformFrameworkJar))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
