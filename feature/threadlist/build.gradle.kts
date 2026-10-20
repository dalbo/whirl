plugins {
    alias(libs.plugins.whirl.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "au.howesthat.whirl.feature.threadlist"
}

dependencies {
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose.material.icons.core)

    testImplementation(project(":core:scraper"))
}
