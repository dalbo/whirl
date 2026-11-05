import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** Pure-JVM Kotlin module with JUnit Jupiter tests that can read the repo's fixtures/ directory. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JAVA_VERSION
            targetCompatibility = JAVA_VERSION
        }
        configureKotlinJvmTarget()

        dependencies {
            add("testImplementation", platform(lib("junit-bom")))
            add("testImplementation", lib("junit-jupiter"))
            add("testImplementation", lib("kotlin-test-junit5"))
            add("testRuntimeOnly", lib("junit-platform-launcher"))
        }

        configureTests()
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
    }
}
