import java.util.Properties

plugins {
    id("launcher.android.application")
    id("launcher.android.hilt")
}

val platformArtifactsDirectory =
    rootProject.extensions.extraProperties["platformArtifactsDirectory"] as File

fun platformArtifact(name: String) = platformArtifactsDirectory.resolve(name)

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
    implementation(project(":core:model"))
    implementation(project(":core:platform"))
    implementation(project(":core:ui"))
    implementation(project(":compat"))
    implementation(project(":feature:launcher:data"))
    implementation(project(":feature:launcher:domain"))
    implementation(project(":feature:launcher:presentation"))
    implementation(project(":feature:home:data"))
    implementation(project(":feature:home:presentation"))

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
    implementation(files(platformArtifact("systemui-shared.jar")))
    implementation(files(platformArtifact("systemui-shared-kotlin.jar")))
    implementation(files(platformArtifact("wm-shell-aidls.jar")))
    implementation(files(platformArtifact("wm-shell-shared.jar")))
    implementation(files(platformArtifact("car-qc-lib.jar")))
    compileOnly(files(platformArtifact("framework.jar")))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(rootProject.tasks.named("verifyPlatformArtifacts"))
}
