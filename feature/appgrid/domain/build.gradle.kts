plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.feature.appgrid.domain"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
}
