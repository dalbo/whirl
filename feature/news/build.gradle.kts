plugins {
    alias(libs.plugins.whirl.android.feature)
}

android {
    namespace = "au.howesthat.whirl.feature.news"
}

dependencies {
    testImplementation(project(":core:database"))
}
