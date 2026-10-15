plugins {
    alias(libs.plugins.pliuskis.android.feature)
    alias(libs.plugins.pliuskis.android.library.compose)
    alias(libs.plugins.pliuskis.android.code.quality)
}

android {
    namespace = "lt.setkus.pliuskis.feature.control"

    // HiveMQ (via core:data) brings Netty jars that each ship these files; the androidTest APK can't hold duplicates.
    packaging {
        resources {
            excludes += listOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
        }
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.data)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.turbine)
}
