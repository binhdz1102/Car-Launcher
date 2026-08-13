plugins {
    id("launcher.android.feature")
    id("launcher.android.hilt")
}

android {
    namespace = "com.android.car.carlauncher.feature.recents.presentation"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":feature:recents:domain"))
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.coroutines.android)
    implementation(libs.timber)

    testImplementation(libs.junit)
}
