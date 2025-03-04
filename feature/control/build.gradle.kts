plugins {
    alias(libs.plugins.pliuskis.android.feature)
    alias(libs.plugins.pliuskis.android.library.compose)
    alias(libs.plugins.pliuskis.android.code.quality)
}

android {
    namespace = "lt.setkus.pliuskis.feature.control"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.data)
}