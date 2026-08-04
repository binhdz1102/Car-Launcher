plugins {
    `kotlin-dsl`
}

group = "com.android.car.carlauncher.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.androidx.room.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "launcher.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "launcher.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "launcher.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidCompose") {
            id = "launcher.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidNavigationCompose") {
            id = "launcher.android.navigation.compose"
            implementationClass = "AndroidNavigationComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "launcher.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidKtlint") {
            id = "launcher.android.ktlint"
            implementationClass = "AndroidKtlintConventionPlugin"
        }
        register("androidDetekt") {
            id = "launcher.android.detekt"
            implementationClass = "AndroidDetektConventionPlugin"
        }
        register("androidJacoco") {
            id = "launcher.android.jacoco"
            implementationClass = "AndroidJacocoConventionPlugin"
        }
        register("androidRoom") {
            id = "launcher.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("rootKtlint") {
            id = "launcher.root.ktlint"
            implementationClass = "RootKtlintConventionPlugin"
        }
        register("rootDetekt") {
            id = "launcher.root.detekt"
            implementationClass = "RootDetektConventionPlugin"
        }
        register("rootJacoco") {
            id = "launcher.root.jacoco"
            implementationClass = "RootJacocoConventionPlugin"
        }
    }
}
