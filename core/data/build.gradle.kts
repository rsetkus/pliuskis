plugins {
    alias(libs.plugins.pliuskis.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            // AndroidConnectivityManager uses androidx.core.content.getSystemService.
            implementation(libs.androidx.core.ktx)
            // androidDataModule uses androidContext().
            implementation(libs.koin)
            implementation(libs.hiveMqClient)
        }
        named("androidHostTest").dependencies {
            implementation(libs.mockk)
        }
    }
}
