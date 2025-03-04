plugins {
    alias(libs.plugins.pliuskis.android.library)
    alias(libs.plugins.pliuskis.android.library.compose)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.pliuskis.android.code.quality)
}

android {
    namespace = "lt.setkus.pliuskis.core.designsystem"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
}