plugins {
    alias(libs.plugins.whirl.android.feature)
    alias(libs.plugins.whirl.serialization)
}

android {
    namespace = "au.howesthat.whirl.feature.writer"
}

dependencies {
    // The preview: WhirlCode to the site's HTML, then the parser every post goes through.
    implementation(project(":core:whirlcode"))
    implementation(project(":core:scraper"))
    implementation(libs.androidx.navigation3.runtime)

    testImplementation(project(":core:database"))
}
