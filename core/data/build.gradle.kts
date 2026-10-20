plugins {
    alias(libs.plugins.whirl.android.library)
    alias(libs.plugins.whirl.hilt)
}

android {
    namespace = "au.howesthat.whirl.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:network"))
    implementation(project(":core:scraper"))
    implementation(project(":core:database"))
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.okhttp.mockwebserver)
}
