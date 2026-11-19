import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.pliuskis.kmp.library)
    // Turns Kotlin Flows, sealed types and suspend functions into Swift-friendly API in the framework.
    alias(libs.plugins.skie)
}

/*
 * The umbrella module that iOS links: one static framework `Shared`. Its Swift API is `initShared(settings)`, the
 * ViewModel factories (src/iosMain) and the exported modules below. Build it with
 * `./gradlew :shared:assembleSharedDebugXCFramework`.
 */
kotlin {
    val frameworkName = "Shared"
    val xcFramework = XCFramework(frameworkName)
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = frameworkName
            isStatic = true
            binaryOption("bundleId", "lt.setkus.pliuskis.shared")
            // The ViewModels, their UI states and the display models. The rest of the graph stays out of the header.
            export(project(":core:presentation"))
            xcFramework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:presentation"))
            // MqttSettings, the argument of initShared.
            api(project(":core:data"))
            implementation(project(":core:domain"))
        }
    }
}
