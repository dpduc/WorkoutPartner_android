// Wraps MediaPipe Pose Landmarker over CameraX's front camera and emits a
// stream of pose-landmark frames. No rep-counting logic lives here — that's
// core-rep-counting (Seam 1), which this module does NOT depend on.
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
