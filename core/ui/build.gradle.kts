plugins {
    alias(libs.plugins.whirl.android.library)
    alias(libs.plugins.whirl.android.compose)
    alias(libs.plugins.whirl.serialization)
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

    // Post bodies for UI tests come from the real parser.
    testImplementation(project(":core:scraper"))
}
