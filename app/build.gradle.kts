plugins {
    alias(libs.plugins.whirl.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release builds get their version from the tag: ./gradlew -Pwhirl.version=1.2.3 → versionName 1.2.3, versionCode 10203.
val whirlVersion = providers.gradleProperty("whirl.version").orElse("0.0.0").get()
val whirlVersionCode = Regex("""^(\d+)\.(\d+)\.(\d+)$""").matchEntire(whirlVersion)
    ?.destructured?.let { (major, minor, patch) -> major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt() }
    ?: error("whirl.version must be MAJOR.MINOR.PATCH, got '$whirlVersion'")

// Signing comes only from the environment of the release workflow; local release builds are unsigned.
val keystoreFile = providers.environmentVariable("WHIRL_KEYSTORE_FILE").orNull

android {
    namespace = "au.howesthat.whirl"

    defaultConfig {
        applicationId = "au.howesthat.whirl"
        versionCode = maxOf(whirlVersionCode, 1)
        versionName = whirlVersion
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = providers.environmentVariable("WHIRL_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("WHIRL_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("WHIRL_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        // F-Droid and reproducible builds: no Google-encrypted dependency blob in the APK.
        includeInApk = false
        includeInBundle = false
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
