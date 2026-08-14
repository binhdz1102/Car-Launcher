plugins {
    id("launcher.android.library")
}

android {
    namespace = "com.android.car.docklib"
}

dependencies {
    implementation(project(":libraries:dock-util"))
    implementation(libs.androidx.recyclerview)
}
