// Build for the Fretboard native application.
//
// Two modules only: `:engine` wraps the prepared engine artifact (the AAR built
// by the core repository) and `:app` is the Compose application. The engine
// artifact is a *prepared vendor payload* — `scripts/prepare_core.py` verifies
// its SHA-256 against `core-release.lock.json` before it is copied into
// `engine/libs/`, which is never committed.
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
        // The prepared engine artifact, installed as a local Maven repository
        // by `scripts/prepare_core.py` after its SHA-256 is verified. The
        // directory is never committed.
        maven { url = uri("engine/maven") }
    }
}

rootProject.name = "fretboard"
include(":app")
include(":engine")
