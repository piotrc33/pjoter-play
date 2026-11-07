plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pl.pjoterplay.app"
    compileSdk = 37

    defaultConfig {
        // Keep in sync with MANAGER_ID in docs/app.js and the manager's entry in docs/apps.json.
        applicationId = "pl.pjoterplay.app"
        // Android 8.0, ~97% of phones: the friends' phones too, not just the Pixel. See CLAUDE.md, Decisions.
        minSdk = 26
        targetSdk = 37
        // From the release tag in CI (keep in sync with .github/workflows/release.yml); local builds stay 1 / 0.1.0.
        versionCode = System.getenv("VERSION_CODE")?.toInt() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
    }

    signingConfigs {
        // pedefka's key (its files and alias keep the app's old name, paggio), the one key for all the apps (CLAUDE.md,
        // Decisions), from the environment so that it stays out of the repo. Keep in sync with
        // .github/workflows/release.yml. Without it a release build comes out unsigned.
        System.getenv("RELEASE_KEYSTORE_FILE")?.let { keystore ->
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = "paggio"
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        debug {
            // Lives next to the release build that friends get from the page.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        warningsAsErrors = true
        disable += "ChromeOsAbiSupport"
        // Versions are pinned to ../reader's on purpose; upgrades are a separate step.
        disable += listOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
