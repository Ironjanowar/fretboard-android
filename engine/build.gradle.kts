// `:engine` isolates the generated UniFFI bindings and the native engine.
//
// It carries no musical logic: it exposes the prepared artifact's API to the
// application and declares the runtime dependency the artifact records
// (`net.java.dev.jna:jna:5.17.0`, the UniFFI Kotlin runtime).
plugins {
    // AGP 9 applies its own built-in Kotlin support.
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.ironjanowar.fretboard.engine"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The artifact version is read from the lock `scripts/prepare_core.py` verifies,
// so the coordinate and the pinned artifact cannot drift apart: bumping the
// engine is one edit in `core-release.lock.json`, not two.
val engineVersion = Regex("\"artifact_version\"\\s*:\\s*\"([^\"]+)\"")
    .find(rootProject.file("core-release.lock.json").readText())
    ?.groupValues?.get(1)
    ?: error("core-release.lock.json declares no artifact_version")

dependencies {
    // The prepared engine artifact: `jni/<abi>/libfretboard_mobile_ffi.so` plus
    // the generated binding classes under `dev.ironjanowar.fretboard.core`.
    // It is installed into the local `engine/maven` repository by
    // `scripts/prepare_core.py`, which verifies its SHA-256 first; that
    // directory is never committed.
    api("${libs.versions.fretboardEngine.get()}:fretboard-engine:$engineVersion")
    api("net.java.dev.jna:jna:5.17.0@aar")
}
