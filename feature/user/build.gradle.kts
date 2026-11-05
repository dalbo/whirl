plugins {
    alias(libs.plugins.whirl.android.feature)
    alias(libs.plugins.whirl.serialization)
}

android {
    namespace = "au.howesthat.whirl.feature.user"
}

dependencies {
    // Recent activity is a thread list: reuse its rows, opening rules and navigation.
    implementation(project(":feature:threadlist"))
    implementation(libs.androidx.navigation3.runtime)
}
