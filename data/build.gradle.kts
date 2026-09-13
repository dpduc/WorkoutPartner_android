// Seam 3 (per spec.md): AccountRepository, SetRepository, TallyRepository,
// RosterRepository. Room-backed (offline-first, ADR-0002) with a background
// sync queue to Firestore (ADR-0001). Owns the Guest -> Account migration
// transaction (ADR-0004).
plugins {
    alias(libs.plugins.android.library)
    // Kotlin compilation comes from AGP's built-in Kotlin support (AGP 9+);
    // org.jetbrains.kotlin.android is deliberately not applied here.
}

android {
    namespace = "com.workoutpartner.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // room-compiler + the KSP plugin land with ticket 05 (data model/schema),
    // once there are @Entity/@Dao classes to generate code from.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
