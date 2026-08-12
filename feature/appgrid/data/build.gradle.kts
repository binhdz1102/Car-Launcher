plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.carlauncher.feature.appgrid.data"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:platform"))
    implementation(project(":feature:appgrid:domain"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coroutines.android)
}
