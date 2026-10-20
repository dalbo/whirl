plugins {
    alias(libs.plugins.whirl.jvm.library)
}

dependencies {
    api(project(":core:model"))
    implementation(libs.jsoup)
}
