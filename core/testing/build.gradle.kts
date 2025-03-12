plugins {
    alias(libs.plugins.pliuskis.android.library)
    alias(libs.plugins.pliuskis.android.code.quality)
}

android {
    namespace = "lt.setkus.pliuskis.testing"
}

dependencies {
    api(libs.kotlinx.coroutines.test)

    implementation(projects.core.data)
    implementation(libs.androidx.test.rules)
}
