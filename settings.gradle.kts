pluginManagement {
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Arcana"

include(":app")

include(":core:core-common")
include(":core:core-domain")
include(":core:core-data")
include(":core:core-database")
include(":core:core-ui")

include(":feature:feature-library")
include(":feature:feature-spreads")
include(":feature:feature-journal")
include(":feature:feature-settings")

include(":service:service-ai")
