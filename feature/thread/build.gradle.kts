plugins {
    alias(libs.plugins.whirl.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "au.howesthat.whirl.feature.thread"
}

dependencies {
    // PostBodyParser turns cached body HTML into blocks.
    implementation(project(":core:scraper"))
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose.material.icons.core)

    testImplementation(project(":core:database"))
    testImplementation(libs.okhttp.mockwebserver)
}
