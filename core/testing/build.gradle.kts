plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.pliuskis.android.code.quality)
}

android {
    namespace = "lt.setkus.pliuskis.testing"
}

dependencies {
    implementation(libs.androidx.test.rules)
}