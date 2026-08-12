plugins {
    id("launcher.android.feature")
}

android {
    namespace = "com.android.car.carlauncher.feature.widgets"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:platform"))
}
