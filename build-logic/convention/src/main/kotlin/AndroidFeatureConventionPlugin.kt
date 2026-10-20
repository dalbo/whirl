import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A screen module: Compose UI, Hilt view models, the data layer and shared UI. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("whirl.android.library")
        pluginManager.apply("whirl.android.compose")
        pluginManager.apply("whirl.hilt")
        dependencies {
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:data"))
            add("implementation", lib("androidx-hilt-viewmodel-compose"))
            add("implementation", lib("androidx-activity-compose"))
            add("implementation", lib("androidx-lifecycle-runtime-compose"))
            add("implementation", lib("androidx-lifecycle-viewmodel-compose"))
        }
    }
}
