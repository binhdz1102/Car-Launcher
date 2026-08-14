plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.appgridlib"

    // The library resource contract is ported before the legacy AOSP implementation. Avoid
    // generating bindings for layouts whose AOSP custom views are not yet in this module.
    buildFeatures {
        viewBinding = false
    }
}

dependencies {
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
}
