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
        versionCode = 1
        versionName = "0.1.0"

        // The engine artifact ships one ABI (arm64-v8a, DEC-10), so the
        // application declares only that: an APK carrying native libraries it
        // cannot load would be larger without being more useful.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

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
}
