package com.yourcompany.facecheck

import ai.faceping.FacePing
import android.app.Application
import android.util.Log

class FaceCheckApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Starts loading the face models in the background, so the first check is quick. With a device token (set in
        // app/build.gradle.kts), this phone is a check-in device for one list; otherwise it runs the sandbox.
        try {
            if (BuildConfig.DEVICE_TOKEN.isBlank())
                FacePing.init(this, sandboxKey = BuildConfig.SANDBOX_KEY)
            else
                FacePing.init(this, deviceToken = BuildConfig.DEVICE_TOKEN.trim())
        } catch (e: Exception) {
            // A missing or malformed key or token: MainActivity shows the message.
            Log.w("FaceCheck", "FacePing setup: ${e.message}")
        }
    }
}
