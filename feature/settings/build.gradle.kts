plugins {
    alias(libs.plugins.whirl.android.feature)
}

android {
    namespace = "au.howesthat.whirl.feature.settings"
}

dependencies {
    implementation(project(":feature:setup"))
    implementation(libs.compose.material.icons.core)
}
