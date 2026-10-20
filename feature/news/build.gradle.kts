plugins {
    alias(libs.plugins.whirl.android.feature)
}

android {
    namespace = "au.howesthat.whirl.feature.news"
}

dependencies {
    implementation(libs.compose.material.icons.core)
}
