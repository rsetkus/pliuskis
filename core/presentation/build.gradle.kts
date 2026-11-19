plugins {
    alias(libs.plugins.pliuskis.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The ViewModels take use cases and expose domain types, so core:domain reaches their users.
            api(project(":core:domain"))
            // The multiplatform ViewModel; the screens that use a ViewModel need its API.
            api(libs.androidx.lifecycle.viewmodel)
        }
    }
}
