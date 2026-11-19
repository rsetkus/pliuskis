plugins {
    alias(libs.plugins.pliuskis.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The use cases expose Flow, Either and Koin modules, so these reach the modules that use core:domain.
            api(libs.kotlinx.coroutines.core)
            api(libs.arrowCore)
            api(libs.koin.core)
            // core:data logs with Kermit through this dependency.
            api(libs.kermit)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
