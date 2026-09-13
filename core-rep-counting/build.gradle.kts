// Seam 1 (per spec.md / ADR context): pure Kotlin, no Android framework or
// camera dependency at all. Testable with plain JUnit against fixture
// landmark-frame data, no instrumentation required.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.junit)
}
