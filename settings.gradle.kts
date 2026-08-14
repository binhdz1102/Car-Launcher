pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CarLauncher"
include(":app")
include(":core:common")
include(":core:model")
include(":core:platform")
include(":core:testing")
include(":core:ui")
include(":compat")
include(":feature:launcher:domain")
include(":feature:launcher:data")
include(":feature:launcher:presentation")
include(":feature:home:domain")
include(":feature:home:data")
include(":feature:home:presentation")
include(":feature:media:domain")
include(":feature:media:data")
include(":feature:media:presentation")
include(":feature:appgrid:domain")
include(":feature:appgrid:data")
include(":feature:appgrid:presentation")
include(":feature:recents:domain")
include(":feature:recents:data")
include(":feature:recents:presentation")
include(":feature:calmmode:domain")
include(":feature:calmmode:data")
include(":feature:calmmode:presentation")
include(":feature:dock:domain")
include(":feature:dock:data")
include(":feature:dock:presentation")
include(":feature:widgets")
include(":libraries:appgrid")
include(":libraries:dock")
include(":libraries:dock-util")
include(":libraries:launcher-common")
include(":test-apps:fixture-app")
include(":test-apps:dock-host")
 
