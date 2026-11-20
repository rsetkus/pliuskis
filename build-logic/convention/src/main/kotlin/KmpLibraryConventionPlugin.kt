import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import lt.setkus.pliuskis.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A shared (Kotlin Multiplatform) library: targets Android, `iosArm64` and `iosSimulatorArm64`, runs `commonTest` on
 * the Android host (`testAndroidHostTest`) and on the iOS simulator (`iosSimulatorArm64Test`) with Kotest, and gets
 * the same ktlint and detekt checks as the Android modules. Its `test` task runs the Android host tests, so
 * `./gradlew test` covers it as it covers the Android modules.
 *
 * The namespace is derived from the module path, e.g. `:core:domain` -> `lt.setkus.pliuskis.core.domain`.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                // The Android target of a KMP module, in place of the Android library plugin.
                apply("com.android.kotlin.multiplatform.library")
                // Kotest runs on non-JVM targets through a KSP-generated entry point.
                apply("com.google.devtools.ksp")
                apply("io.kotest")
                apply("pliuskis.code.quality")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                configureAndroidTarget(this, namespaceFor(path))
                iosArm64()
                iosSimulatorArm64()
                configureSharedTestDependencies(this)
            }

            // Kotest on the Android host runs on the JUnit Platform.
            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
            }

            // A KMP module has no `test` task, so `./gradlew test` (as CI runs it) would skip its tests.
            tasks.register(TEST_TASK) {
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                description = "Runs the commonTest tests on the Android host, like `test` in an Android module."
                dependsOn(HOST_TEST_TASK)
            }

            // ktlint lints every Kotlin source directory, including the Kotest entry points KSP generates.
            val buildDir = layout.buildDirectory.get().asFile
            tasks.withType<SourceTask>().matching { it.name.startsWith(KTLINT_TASK_PREFIX) }.configureEach {
                exclude { it.file.startsWith(buildDir) }
            }

            // detekt only looks at src/main and src/test by default; a KMP module keeps its code elsewhere.
            extensions.configure<DetektExtension> {
                source.setFrom(KMP_SOURCE_DIRS)
            }
        }
    }

    private fun configureAndroidTarget(kotlin: KotlinMultiplatformExtension, moduleNamespace: String) {
        (kotlin as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
            namespace = moduleNamespace
            compileSdk = COMPILE_SDK
            minSdk = MIN_SDK
            compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
            withHostTest { }
        }
    }

    private fun Project.configureSharedTestDependencies(kotlin: KotlinMultiplatformExtension) {
        kotlin.sourceSets.named("commonTest").configure {
            dependencies {
                implementation(libs.findLibrary("kotest.framework.engine").get())
                implementation(libs.findLibrary("kotest.assertions.core").get())
                implementation(libs.findLibrary("turbine").get())
                implementation(libs.findLibrary("kotlinx.coroutines.test").get())
            }
        }
        kotlin.sourceSets.named("androidHostTest").configure {
            dependencies {
                implementation(libs.findLibrary("kotest.runner.junit5").get())
                // Gradle 9 no longer adds the JUnit Platform launcher to the test runtime classpath itself.
                runtimeOnly(libs.findLibrary("junit.platform.launcher").get())
            }
        }
    }

    private fun namespaceFor(projectPath: String): String = NAMESPACE_ROOT + projectPath.replace(':', '.')

    private companion object {
        const val NAMESPACE_ROOT = "lt.setkus.pliuskis"
        const val KTLINT_TASK_PREFIX = "runKtlint"
        const val TEST_TASK = "test"
        const val HOST_TEST_TASK = "testAndroidHostTest"

        val KMP_SOURCE_DIRS = listOf(
            "src/commonMain/kotlin",
            "src/androidMain/kotlin",
            "src/iosMain/kotlin",
            "src/commonTest/kotlin",
            "src/androidHostTest/kotlin",
            "src/iosTest/kotlin",
        )
    }
}
