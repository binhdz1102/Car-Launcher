plugins {
    id("launcher.android.feature")
}

android {
    namespace = "com.android.car.carlauncher.core.ui"
}

dependencies {
    implementation(libs.timber)
}
