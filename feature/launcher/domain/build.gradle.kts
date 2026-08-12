plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.feature.launcher.domain"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":feature:home:domain"))
    implementation(libs.coroutines.core)
    implementation(libs.dagger.hilt.android)

    testImplementation(libs.junit)
}
