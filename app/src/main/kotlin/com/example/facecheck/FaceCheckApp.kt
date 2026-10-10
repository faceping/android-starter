package com.example.facecheck

import ai.faceping.FacePing
import android.app.Application
import android.util.Log

class FaceCheckApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Starts loading the face models in the background, so the first check is quick.
        try {
            FacePing.init(this, sandboxKey = BuildConfig.SANDBOX_KEY)
        } catch (e: Exception) {
            // A missing or malformed key: MainActivity shows the message.
            Log.w("FaceCheck", "FacePing setup: ${e.message}")
        }
    }
}
