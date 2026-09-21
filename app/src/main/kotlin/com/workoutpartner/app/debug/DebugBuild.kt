package com.workoutpartner.app.debug

import android.content.Context
import android.content.pm.ApplicationInfo

/** Whether this is a debuggable build — gates developer-only tools (the pose overlay, the debug video source) so release builds never show or look for them. */
fun Context.isDebuggableBuild(): Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
