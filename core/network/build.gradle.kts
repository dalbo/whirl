plugins {
    alias(libs.plugins.whirl.jvm.library)
    alias(libs.plugins.whirl.serialization)
}

dependencies {
    api(project(":core:model"))
    api(libs.okhttp)
    implementation(libs.okhttp.coroutines)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.jsoup)
    compileOnly(libs.jspecify)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
