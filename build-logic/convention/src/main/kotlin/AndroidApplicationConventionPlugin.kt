import com.android.build.api.dsl.ApplicationExtension
import lt.setkus.pliuskis.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // AGP 9 compiles Kotlin itself (built-in Kotlin), so no separate Kotlin Android plugin is applied.
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = TARGET_SDK
                defaultConfig.minSdk = MIN_SDK
                defaultConfig.vectorDrawables.useSupportLibrary = true
                packaging.resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            }
        }
    }
}