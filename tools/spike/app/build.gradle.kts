plugins {
    id("com.android.application")
}

android {
    namespace = "au.howesthat.whirl.spike"
    compileSdk = 37
    defaultConfig {
        applicationId = "au.howesthat.whirl.spike"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "spike"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
}
