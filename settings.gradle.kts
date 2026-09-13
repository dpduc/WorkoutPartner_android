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

rootProject.name = "WorkoutPartner"

// Module split mirrors the seams described in .scratch/workout-partner-v1/spec.md.
include(":app")
include(":core-pose-tracking")
include(":core-rep-counting")
include(":core-streaks")
include(":data")
