plugins {
    alias(libs.plugins.whirl.android.feature)
    alias(libs.plugins.whirl.serialization)
}

android {
    namespace = "au.howesthat.whirl.feature.thread"
}

dependencies {
    // PostBodyParser turns cached body HTML into blocks.
    implementation(project(":core:scraper"))
    implementation(libs.androidx.navigation3.runtime)

    testImplementation(project(":core:database"))
    testImplementation(libs.okhttp.mockwebserver)
}
