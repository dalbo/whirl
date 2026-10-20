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
include(":core:notifications")
include(":feature:setup")
include(":feature:settings")
include(":feature:threadlist")
include(":feature:forums")
include(":feature:thread")
include(":feature:news")
include(":feature:user")
