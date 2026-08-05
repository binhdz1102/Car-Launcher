plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.core.common"
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.timber)
}
