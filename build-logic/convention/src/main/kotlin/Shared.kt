import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.Directory
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal object Sdk {
    const val COMPILE = 37
    const val TARGET = 36
    const val MIN = 26
}

internal val JAVA_VERSION = JavaVersion.VERSION_17

internal fun Project.configureKotlinJvmTarget() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(true)
        }
    }
}

internal val Project.libs
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

internal fun Project.lib(alias: String) = libs.findLibrary(alias).get()

/** Every test task reads the repo's fixtures/ (`whirl.fixtures`), declared as an input so a changed fixture reruns it. */
internal fun Project.configureTests() {
    val fixturesDir: Directory = rootProject.layout.projectDirectory.dir("fixtures")
    tasks.withType<Test>().configureEach {
        systemProperty("whirl.fixtures", fixturesDir.asFile.absolutePath)
        inputs.dir(fixturesDir).withPathSensitivity(PathSensitivity.RELATIVE)
        timeout.set(java.time.Duration.ofMinutes(10))
    }
}
