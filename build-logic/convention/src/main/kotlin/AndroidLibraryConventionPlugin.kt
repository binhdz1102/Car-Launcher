import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("launcher.android.ktlint")
        pluginManager.apply("launcher.android.detekt")
        pluginManager.apply("launcher.android.jacoco")

        extensions.configure<LibraryExtension> {
            compileSdk = 36

            defaultConfig {
                // Keep library lint/API analysis aligned with the application contract.
                minSdk = 34
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                project.file("consumer-rules.pro")
                    .takeIf { it.isFile }
                    ?.let { consumerRules -> consumerProguardFiles(consumerRules) }
            }

            buildFeatures {
                viewBinding = true
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            packaging {
                resources {
                    excludes += "/META-INF/{AL2.0,LGPL2.1}"
                }
            }
        }

        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
