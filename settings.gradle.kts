pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "whirl"

include(":app")
include(":core:model")
include(":core:scraper")
include(":core:network")
include(":core:database")
include(":core:data")
include(":core:ui")
include(":feature:setup")
include(":feature:settings")
