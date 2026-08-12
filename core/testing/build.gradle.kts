plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.core.testing"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:platform"))
    implementation(libs.coroutines.core)
}
