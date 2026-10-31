plugins {
    alias(libs.plugins.whirl.android.library)
    alias(libs.plugins.whirl.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "au.howesthat.whirl.core.ui"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.browser)
    implementation(libs.compose.material.icons.core)
    implementation(libs.kotlinx.serialization.json)
}
