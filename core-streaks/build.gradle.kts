// Seam 2 (per spec.md): a pure function of Active Day history, so no
// persistence or clock dependency lives inside this module — "today" is
// injected by the caller.
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
