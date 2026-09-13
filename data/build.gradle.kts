// Seam 3 (per spec.md): AccountRepository, SetRepository, TallyRepository,
// RosterRepository. Room-backed (offline-first, ADR-0002) with a background
// sync queue to Firestore (ADR-0001). Owns the Guest -> Account migration
// transaction (ADR-0004).
//
// Ticket 05 (data model/Room schema) added the KSP plugin + room-compiler
// (there are now @Entity/@Dao classes to generate code from) and a
// dependency on core-rep-counting (Exercise, reused across SetEntity/
// RoutineStepEntity/TallyEntity rather than duplicated — same shared-
// vocabulary pattern ticket 03 used) and core-streaks (AccountEntity's
// default Weekly Target).
plugins {
    alias(libs.plugins.android.library)
    // Kotlin compilation comes from AGP's built-in Kotlin support (AGP 9+);
    // org.jetbrains.kotlin.android is deliberately not applied here.
    alias(libs.plugins.ksp)
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

ksp {
    // Room schema history, checked in under data/schemas/ — useful once
    // this schema needs its first migration.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core-rep-counting"))
    implementation(project(":core-streaks"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
