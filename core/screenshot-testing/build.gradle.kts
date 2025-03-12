plugins {
    alias(libs.plugins.pliuskis.android.library)
    alias(libs.plugins.pliuskis.android.code.quality)
}

android {
    namespace = "lt.setkus.pliuskis.core.testing"
}

dependencies {
    api(libs.bundles.androidx.compose.ui.test)
}