plugins {
    alias(libs.plugins.pliuskis.android.feature)
    alias(libs.plugins.pliuskis.android.library.compose)
    alias(libs.plugins.pliuskis.android.code.quality)
    alias(libs.plugins.paparazzi)
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
    implementation(projects.core.presentation)

    implementation(libs.timber)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.turbine)

    // Paparazzi snapshot tests are JUnit 4; the vintage engine runs them on the JUnit Platform next to Kotest
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
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
