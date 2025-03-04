import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class CodeQualityConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            with(pluginManager) {
                apply("org.jlleitschuh.gradle.ktlint")
                apply("io.gitlab.arturbosch.detekt")
            }

            dependencies {
                add("detektPlugins", "io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
            }

            tasks.named("detekt") {
                mustRunAfter(tasks.named("runKtlintFormatOverKotlinScripts"))
            }

            // execute detekt before build task
            tasks.named("check") {
                dependsOn("detekt")
            }

            // execute detekt before build building apk
            tasks.matching { it.name.startsWith("assemble") || it.name.startsWith("bundle") }.configureEach {
                dependsOn("detekt")
            }
        }
    }
}