plugins {
    alias(libs.plugins.whirl.android.feature)
}

android {
    namespace = "au.howesthat.whirl.feature.forums"
}

dependencies {
    testImplementation(project(":core:database"))
}
