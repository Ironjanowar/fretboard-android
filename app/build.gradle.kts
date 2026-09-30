// The Fretboard application module.
//
// The release variant is signed with the persistent release key kept *outside*
// this repository (decision DEC-08): the build fails closed when the signing
// configuration is missing, so an unsigned or debug-signed release can never be
// produced by accident.
import java.util.Properties

plugins {
    // AGP 9 applies its own built-in Kotlin support, so `kotlin.android` must
    // not be applied here; the Compose compiler plugin still is.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.ironjanowar.fretboard"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.ironjanowar.fretboard"
        minSdk = 29
        targetSdk = 37
        versionCode = 18
        versionName = "0.8.5"

        // The application and the pinned engine expose the same two ABIs: one
        // for physical devices and one for the emulator verification path.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        // The instrumented suite runs through the Android JUnit runner on a
        // physical device or the x86_64 emulator.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A24: the instrumentation tests can be pointed at a variant, so the *release* build can be
    // tested and not only debug: `-PtestBuildType=release`. The default stays `debug`, which is
    // what a developer runs, and an explicit value is what a release verification passes.
    testBuildType = (findProperty("testBuildType") as String?) ?: "debug"

    signingConfigs {
        create("release") {
            val configuration = file(
                System.getenv("FRETBOARD_SIGNING")
                    ?: "${System.getProperty("user.home")}/.fretboard-signing/keystore.properties"
            )
            if (!configuration.exists()) {
                error(
                    "Release signing configuration is missing: $configuration. " +
                        "The release variant is never built without the persistent key."
                )
            }
            val properties = Properties()
            configuration.inputStream().use { properties.load(it) }
            storeFile = file(properties.getProperty("storeFile"))
            storePassword = properties.getProperty("storePassword")
            keyAlias = properties.getProperty("keyAlias")
            keyPassword = properties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // Shrinking is not enabled yet: R8 configuration and its verification
            // belong to the release-hardening task, and an unverified shrinker
            // would risk changing behaviour rather than size.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    // The suggestion lifecycle's virtual-time harness (A16): a StandardTestDispatcher
    // proves the request left the caller's thread, and a controllable fake proves a
    // late answer cannot replace a newer one.
    testImplementation(libs.kotlinx.coroutines.test)

    // Provides ComponentActivity for createComposeRule() in device-side
    // composable tests without adding a test activity to the release manifest.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // The device-side regression test: ActivityScenario.recreate() with the
    // Compose test rule. Test-only dependencies; nothing here reaches the APK.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Compose's test artifact can resolve an older Espresso transitively. Pin
    // 3.7 explicitly because older input injection reflects on an API removed
    // from current Android releases.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
