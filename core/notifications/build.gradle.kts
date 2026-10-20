plugins {
    alias(libs.plugins.whirl.android.library)
    alias(libs.plugins.whirl.hilt)
}

android {
    namespace = "au.howesthat.whirl.core.notifications"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(libs.androidx.core.ktx)
    api(libs.work.runtime)
    api(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.work.testing)
    testImplementation(libs.okhttp.mockwebserver)
}
