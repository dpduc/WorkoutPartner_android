// Compose UI, DI wiring, WorkManager/AlarmManager. Depends on every other
// module so the multi-module graph is proven end-to-end even though none of
// them have feature logic yet (that's out of scope for ticket 01).
plugins {
    alias(libs.plugins.android.application)
    // Kotlin compilation comes from AGP's built-in Kotlin support (AGP 9+);
    // org.jetbrains.kotlin.android is deliberately not applied here.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.workoutpartner.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.workoutpartner.app"
        // Min SDK ~26 per spec.md's "Further Notes": assumed for MediaPipe
        // GPU-delegate performance, not yet validated against a real device
        // matrix. Carry that caveat forward, don't treat it as settled.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core-pose-tracking"))
    implementation(project(":core-rep-counting"))
    implementation(project(":core-streaks"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    // camera-core itself comes transitively via core-pose-tracking's `api`
    // dependency on it (PoseTracker.start(...)'s own signature needs it) —
    // only camera-view (PreviewView, used directly in SessionScreens.kt) is
    // a real, direct dependency of this module.
    implementation(libs.androidx.camera.view)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
