import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** Android library; unit tests use JUnit 4 + Robolectric so they can touch Room and DataStore. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            compileSdk = Sdk.COMPILE
            defaultConfig {
                minSdk = Sdk.MIN
                consumerProguardFiles("consumer-rules.pro")
            }
            compileOptions {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }
            testOptions {
                unitTests.isIncludeAndroidResources = true
            }
        }
        configureKotlinJvmTarget()
        val fixturesDir = rootProject.layout.projectDirectory.dir("fixtures")
        tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
            systemProperty("whirl.fixtures", fixturesDir.asFile.absolutePath)
            timeout.set(java.time.Duration.ofMinutes(10))
            failOnNoDiscoveredTests.set(false)
        }
        dependencies {
            add("testImplementation", lib("junit4"))
            add("testImplementation", lib("robolectric"))
            add("testImplementation", lib("androidx-test-core"))
            add("testImplementation", lib("androidx-test-junit"))
            add("testImplementation", lib("kotlinx-coroutines-test"))
            add("testImplementation", lib("turbine"))
            add("testImplementation", lib("kotlin-test-junit"))
        }
    }
}
