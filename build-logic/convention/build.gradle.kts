plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "whirl.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("jvmLibrary") {
            id = "whirl.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
