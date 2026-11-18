import com.android.build.api.dsl.LibraryExtension
import lt.setkus.pliuskis.configureKotlinAndroid
import lt.setkus.pliuskis.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                // AGP 9 compiles Kotlin itself (built-in Kotlin), so no separate Kotlin Android plugin is applied.
                apply("com.android.library")
                apply("org.jetbrains.kotlin.plugin.serialization")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                // AGP 9 has no targetSdk in a library's defaultConfig; it sets the test APK and lint values instead.
                testOptions.targetSdk = TARGET_SDK
                lint.targetSdk = TARGET_SDK
                testOptions.unitTests.all {
                    it.useJUnitPlatform()
                    // Kotest scans the whole test classpath at start-up; on the AGP 9 / Kotlin 2.4 classpath that
                    // no longer fits in Gradle's default 512m test heap.
                    it.maxHeapSize = "1g"
                }
            }
            dependencies {
                add("testImplementation", kotlin("test"))
                // Gradle 9 no longer adds the JUnit Platform launcher to the test runtime classpath itself
                add("testRuntimeOnly", libs.findLibrary("junit.platform.launcher").get())
                add("implementation", libs.findLibrary("androidx.tracing.ktx").get())
                add("implementation", libs.findLibrary("kotlinx.serialization.json").get())
                add("api", libs.findLibrary("kotlinx.datetime").get())
                add("implementation", libs.findLibrary("arrowCore").get())
            }
        }
    }
}