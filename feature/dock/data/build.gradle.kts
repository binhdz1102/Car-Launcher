plugins {
    id("launcher.android.library")
    id("launcher.android.hilt")
}

android {
    namespace = "com.android.car.carlauncher.feature.dock.data"
}

dependencies {
    implementation(project(":feature:dock:domain"))
    implementation(project(":core:model"))
    implementation(project(":core:platform"))
    implementation(project(":libraries:dock"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
}
