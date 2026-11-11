plugins {
    alias(libs.plugins.whirl.android.feature)
    alias(libs.plugins.whirl.serialization)
}

android {
    namespace = "au.howesthat.whirl.feature.settings"
}

dependencies {
    implementation(project(":feature:setup"))
    implementation(project(":core:notifications"))
    implementation(libs.androidx.navigation3.runtime)
}
