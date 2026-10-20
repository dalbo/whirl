plugins {
    alias(libs.plugins.whirl.android.feature)
}

android {
    namespace = "au.howesthat.whirl.feature.forums"
}

dependencies {
    implementation(libs.compose.material.icons.core)
}
