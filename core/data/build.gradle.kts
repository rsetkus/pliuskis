plugins {
    alias(libs.plugins.pliuskis.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(libs.kotlinx.serialization.json)
            // The Cognito sign-in's HTTPS calls (managed login token endpoint, DeleteUser).
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies {
            // AndroidConnectivityManager uses androidx.core.content.getSystemService.
            implementation(libs.androidx.core.ktx)
            // androidDataModule uses androidContext().
            implementation(libs.koin)
            implementation(libs.hiveMqClient)
            implementation(libs.ktor.client.android)
            // The managed login opens in a Custom Tab.
            implementation(libs.androidx.browser)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
        named("androidHostTest").dependencies {
            implementation(libs.mockk)
        }
    }
}
