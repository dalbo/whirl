plugins {
    alias(libs.plugins.whirl.android.application)
    alias(libs.plugins.whirl.android.compose)
    alias(libs.plugins.whirl.hilt)
    alias(libs.plugins.kotlin.serialization)
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
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:setup"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:threadlist"))
    implementation(project(":feature:forums"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose.material.icons.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
}
