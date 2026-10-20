plugins {
    alias(libs.plugins.whirl.android.library)
    alias(libs.plugins.whirl.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "au.howesthat.whirl.core.database"
}

// MigrationTestHelper reads the exported schemas from assets; Robolectric serves unit-test assets.
androidComponents {
    onVariants { variant ->
        variant.hostTests.values.forEach { it.sources.assets?.addStaticSourceDirectory("$projectDir/schemas") }
    }
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
