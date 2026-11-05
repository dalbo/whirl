plugins {
    alias(libs.plugins.whirl.android.feature)
    alias(libs.plugins.whirl.serialization)
}

android {
    namespace = "au.howesthat.whirl.feature.threadlist"
}

dependencies {
    implementation(libs.androidx.navigation3.runtime)

    testImplementation(project(":core:scraper"))
    testImplementation(project(":core:database"))
}
