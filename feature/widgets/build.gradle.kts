plugins {
    id("launcher.android.feature")
    id("launcher.android.hilt")
}

android {
    namespace = "com.android.car.carlauncher.feature.widgets"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:platform"))
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.timber)
}
