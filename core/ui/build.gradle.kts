plugins {
    alias(libs.plugins.whirl.android.library)
    alias(libs.plugins.whirl.android.compose)
}

android {
    namespace = "au.howesthat.whirl.core.ui"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
}
