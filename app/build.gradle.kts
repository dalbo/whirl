import java.util.Properties

plugins {
    alias(libs.plugins.whirl.android.application)
    alias(libs.plugins.whirl.android.compose)
    alias(libs.plugins.whirl.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// The version lives in app/version.properties (F-Droid reads it at each tag); the release workflow checks the tag.
val versionProps = Properties().apply {
    load(providers.fileContents(layout.projectDirectory.file("version.properties")).asText.get().reader())
}
val whirlVersion: String = versionProps.getProperty("versionName")
val whirlVersionCode = versionProps.getProperty("versionCode").toInt()
Regex("""^(\d+)\.(\d+)\.(\d+)$""").matchEntire(whirlVersion)?.destructured
    ?.let { (major, minor, patch) -> major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt() }
    .let { check(it == whirlVersionCode) { "version.properties: versionCode $whirlVersionCode does not match versionName $whirlVersion" } }

// Signing comes only from the environment of the release workflow; local release builds are unsigned.
val keystoreFile = providers.environmentVariable("WHIRL_KEYSTORE_FILE").orNull

android {
    namespace = "au.howesthat.whirl"

    defaultConfig {
        applicationId = "au.howesthat.whirl"
        versionCode = whirlVersionCode
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
    implementation(project(":core:notifications"))
    implementation(project(":feature:setup"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:threadlist"))
    implementation(project(":feature:forums"))
    implementation(project(":feature:thread"))
    implementation(project(":feature:news"))
    implementation(project(":feature:user"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose.material.icons.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
}
