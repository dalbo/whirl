plugins {
    alias(libs.plugins.whirl.android.library)
    alias(libs.plugins.whirl.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "au.howesthat.whirl.core.database"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.room.testing)
}
