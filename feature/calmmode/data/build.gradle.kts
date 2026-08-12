plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.feature.calmmode.data"
}

dependencies {
    implementation(project(":feature:calmmode:domain"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coroutines.android)
}
