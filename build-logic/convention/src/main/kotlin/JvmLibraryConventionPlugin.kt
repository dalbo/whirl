import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
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

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            add("testImplementation", platform(libs.findLibrary("junit-bom").get()))
            add("testImplementation", libs.findLibrary("junit-jupiter").get())
            add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5")
            add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
        }

        val fixturesDir = rootProject.layout.projectDirectory.dir("fixtures")
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            systemProperty("whirl.fixtures", fixturesDir.asFile.absolutePath)
            timeout.set(java.time.Duration.ofMinutes(10))
            inputs.dir(fixturesDir).withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
        }
    }
}
