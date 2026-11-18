const val TARGET_SDK = 34
const val MIN_SDK = 24

// Lifecycle 2.10, Navigation 2.9 and Koin 4.1 need compileSdk 35. Paparazzi 1.x fails on the android.jar of
// compileSdk 36 or later (see docs/testing.md), so newer releases that need 36+ wait for Paparazzi 2.x.
const val COMPILE_SDK = 35
