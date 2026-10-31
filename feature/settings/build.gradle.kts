plugins {
    alias(libs.plugins.whirl.android.feature)
}

android {
    namespace = "au.howesthat.whirl.feature.settings"
}

dependencies {
    implementation(project(":feature:setup"))
    implementation(project(":core:notifications"))
    implementation(libs.compose.material.icons.core)
}
