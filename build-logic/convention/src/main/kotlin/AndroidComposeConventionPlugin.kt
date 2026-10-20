import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType

/** Compose for an Android application or library module. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.findByType<LibraryExtension>()?.buildFeatures?.compose = true
        extensions.findByType<ApplicationExtension>()?.buildFeatures?.compose = true
        dependencies {
            val bom = platform(lib("compose-bom"))
            add("implementation", bom)
            add("implementation", lib("compose-ui"))
            add("implementation", lib("compose-material3"))
            add("implementation", lib("compose-ui-tooling-preview"))
            add("debugImplementation", lib("compose-ui-tooling"))
        }
    }
}
