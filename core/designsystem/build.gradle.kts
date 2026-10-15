plugins {
    alias(libs.plugins.pliuskis.android.library)
    alias(libs.plugins.pliuskis.android.library.compose)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.pliuskis.android.code.quality)
    alias(libs.plugins.paparazzi)
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

    // Paparazzi snapshot tests are JUnit 4; the vintage engine runs them on the JUnit Platform next to Kotest
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.vintage.engine)

    // Compose UI behaviour tests (instrumented)
    androidTestImplementation(libs.androidx.compose.ui.test.junit)
    androidTestImplementation(libs.junit.ext)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// LayoutLib needs Guava's -jre variant; without this the -android variant wins on the test classpath.
// See https://github.com/cashapp/paparazzi/issues/906
dependencies.constraints {
    add("testImplementation", "com.google.guava:guava") {
        attributes {
            attribute(
                TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM)
            )
        }
        because("LayoutLib and sdk-common depend on Guava's -jre published variant.")
    }
}
