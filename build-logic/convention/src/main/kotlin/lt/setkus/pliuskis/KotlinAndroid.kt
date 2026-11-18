package lt.setkus.pliuskis

import COMPILE_SDK
import MIN_SDK
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension,
) {
    commonExtension.apply {
        compileSdk = COMPILE_SDK

        defaultConfig.minSdk = MIN_SDK

        // Up to Java 11 APIs are available through desugaring
        // https://developer.android.com/studio/write/java11-minimal-support-table
        compileOptions.sourceCompatibility = JavaVersion.VERSION_11
        compileOptions.targetCompatibility = JavaVersion.VERSION_11
        compileOptions.isCoreLibraryDesugaringEnabled = true
    }

    configureKotlin()

    dependencies {
        add("coreLibraryDesugaring", libs.findLibrary("android.desugarJdkLibs").get())
        add("implementation", libs.findLibrary("arrowCore").get())
    }
}

/**
 * Configure base Kotlin options
 */
private fun Project.configureKotlin() {
    // Use withType to workaround https://youtrack.jetbrains.com/issue/KT-55947
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            // Set JVM target to 11
            jvmTarget.set(JvmTarget.JVM_11)
            // Treat all Kotlin warnings as errors (disabled by default)
            // Override by setting warningsAsErrors=true in your ~/.gradle/gradle.properties
            val warningsAsErrors = providers.gradleProperty("warningsAsErrors").orNull
            allWarningsAsErrors.set(warningsAsErrors.toBoolean())
            // Enable experimental coroutines APIs, including Flow
            optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
}