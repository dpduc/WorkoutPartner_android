// Wraps MediaPipe Pose Landmarker over CameraX's front camera and emits a
// stream of pose-landmark frames. No rep-counting logic (state machines,
// thresholds) lives here — that's core-rep-counting (Seam 1).
//
// Correction to ticket 01's original comment here: this module DOES depend
// on core-rep-counting after all, but only for its shared, side-agnostic
// vocabulary (Point3D/Landmark/PoseLandmarkFrame) — not circular, since
// core-rep-counting depends on nothing. Per Landmark.kt's own doc comment,
// picking a side per joint (e.g. whichever shoulder is more visible) is
// explicitly this module's job (ticket 03), which requires constructing
// core-rep-counting's actual types rather than duplicating them.
plugins {
    alias(libs.plugins.android.library)
    // Kotlin compilation comes from AGP's built-in Kotlin support (AGP 9+);
    // org.jetbrains.kotlin.android is deliberately not applied here.
}

android {
    namespace = "com.workoutpartner.core.posetracking"
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
    implementation(project(":core-rep-counting"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
