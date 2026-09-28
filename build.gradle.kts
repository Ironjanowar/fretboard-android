// Root build script: the plugin versions live in `gradle/libs.versions.toml`,
// pinned to the tuple recorded in the core repository's `docs/toolchains.md`.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
