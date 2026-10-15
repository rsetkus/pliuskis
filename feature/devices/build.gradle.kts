plugins {
    alias(libs.plugins.pliuskis.android.feature)
    alias(libs.plugins.pliuskis.android.library.compose)
    alias(libs.plugins.pliuskis.android.code.quality)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "lt.setkus.pliuskis.feature.devices"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // HiveMQ (via core:data) brings Netty jars that each ship these files; the androidTest APK can't hold duplicates.
    packaging {
        resources {
            excludes += listOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
        }
    }
}

// The ViewModel tests swap Dispatchers.Main with setMain/resetMain, which leaves it unusable for the rest of that
// JVM, and Paparazzi snapshots then fail to render. One JVM per test class keeps the two kinds of tests apart.
tasks.withType<Test>().configureEach {
    setForkEvery(1L)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.data)

    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.turbine)
    testImplementation(libs.hiveMqClient)

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
