pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KleidiTenant"

// Same layering as the staff Android app (and the iOS KleidiKit):
// core (pure Kotlin) ← api (pure Kotlin, OkHttp) ← ui (Compose kit) ← app.
include(":core", ":api", ":ui", ":app")
