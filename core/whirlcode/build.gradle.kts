plugins {
    alias(libs.plugins.whirl.jvm.library)
}

dependencies {
    // The tests compare the converter's HTML with the site's through the app's own parser.
    testImplementation(project(":core:scraper"))
    testImplementation(libs.jsoup)
}
