package com.yourcompany.facecheck

import ai.faceping.EnrollOutcome
import ai.faceping.FaceCameraView
import ai.faceping.FaceGuideState
import ai.faceping.FacePing
import ai.faceping.FacePingModelException
import ai.faceping.FacePingSetupException
import ai.faceping.SyncResult
import ai.faceping.SyncStatus
import ai.faceping.VerifyOutcome
import android.graphics.Outline
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * FaceCheck: enrol your face, then verify it with a live check, with or without a network. As a check-in device (a
 * device token in app/build.gradle.kts), it verifies PERSON_ID once they've synced from your practice list.
 */
class MainActivity : ComponentActivity() {
    /** Who to enrol and verify: "me" in the sandbox; on a check-in device, the personId you enrolled on the server. */
    private val personId = BuildConfig.PERSON_ID.ifBlank { "me" }

    private lateinit var camera: FaceCameraView
    private lateinit var enrollButton: Button
    private lateinit var verifyButton: Button
    private lateinit var resultTitle: TextView
    private lateinit var resultNote: TextView
    private lateinit var network: TextView
    private lateinit var connectivity: ConnectivityManager
    private var faceping: FacePing? = null
    private var busy = false

    @Volatile
    private var offline = true

    // The default network's state, as the callbacks report it (reading activeNetwork inside them is racy).
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            setOffline(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))

        override fun onLost(network: Network) = setOffline(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val root = findViewById<View>(R.id.root)
        val padTop = root.paddingTop
        val padBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = padTop + bars.top, bottom = padBottom + bars.bottom)
            insets
        }

        camera = findViewById(R.id.camera)
        enrollButton = findViewById(R.id.enroll)
        verifyButton = findViewById(R.id.verify)
        resultTitle = findViewById(R.id.resultTitle)
        resultNote = findViewById(R.id.resultNote)
        network = findViewById(R.id.network)

        // Round the camera's corners like the card behind it.
        findViewById<View>(R.id.cameraCard).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, 18 * resources.displayMetrics.density)
            }
            clipToOutline = true
        }

        camera.cameraFailedListener = FaceCameraView.CameraFailedListener { message -> show("Camera unavailable", message, R.color.ink) }
        enrollButton.setOnClickListener { onEnroll() }
        verifyButton.setOnClickListener { onVerify() }

        connectivity = getSystemService(ConnectivityManager::class.java)
        offline = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true
        showNetwork()
        connectivity.registerDefaultNetworkCallback(networkCallback)

        faceping = try {
            FacePing.current
        } catch (e: IllegalStateException) {
            show("Setup problem", "Set your sandbox key (fp_test_…) or a device token (fpd_…) in app/build.gradle.kts.", R.color.red)
            enable(enrollButton, false)
            null
        }
        // A check-in device gets its people from the server: no enrolling here.
        val fp = faceping
        if (fp != null && !fp.isSandbox) {
            enrollButton.visibility = View.GONE
            show("Syncing", "Downloading this list's people…", R.color.ink)
            // Every sync (including the automatic ones, every 5 minutes) updates the screen.
            lifecycleScope.launch { fp.synced.collect { synced(it) } }
        }

        // Activates the sandbox key (or, on a check-in device, downloads its list). A face enrolled earlier may still
        // be on the phone (sandbox faces are deleted 24 hours after enrolment).
        lifecycleScope.launch {
            if (fp == null) return@launch
            try {
                val result = fp.sync()
                // Shown here too, in case this first sync finished before the collector above started listening.
                if (!fp.isSandbox) synced(result)
            } catch (e: FacePingSetupException) {
                show(problemTitle(), e.message, R.color.red)
            } catch (e: FacePingModelException) {
                show("Face check unavailable", e.message, R.color.red)
            }
            setEnrolled(personId in fp.enrolledIds)
        }
    }

    /** On a check-in device: open Verify once the person is here, and say what the sync found. */
    private fun synced(result: SyncResult) {
        val here = faceping?.enrolledIds?.contains(personId) == true
        setEnrolled(here)
        when (result.status) {
            SyncStatus.REVOKED -> show("Device token not valid", "It was revoked or mistyped, or its list was deleted. Add the device again and use the new token.", R.color.red)
            SyncStatus.GONE -> show("List finished", "This list's faces have been deleted, so there's no one to check.", R.color.ink)
            SyncStatus.UNAVAILABLE -> show("Couldn't sync", "No connection to FacePing. The people synced before still work until the lease runs out.", R.color.ink)
            else -> show("${result.enrolled} people synced",
                if (here) "$personId is here: tap Verify." else "$personId isn't on this list yet. Enrol them on your server, then reopen the app.", R.color.ink)
        }
    }

    private fun problemTitle() = if (faceping?.isSandbox == false) "Device token problem" else "Sandbox key problem"

    override fun onDestroy() {
        connectivity.unregisterNetworkCallback(networkCallback)
        super.onDestroy()
    }

    private fun onEnroll() = runAction {
        val fp = faceping ?: return@runAction
        show("Hold still", "Taking the photo…", R.color.ink)
        camera.guideState = FaceGuideState.WORKING
        val photo = camera.capture()
        val result = fp.enroll(personId, photo)
        camera.guideState = FaceGuideState.READY
        if (result.outcome == EnrollOutcome.ENROLLED) {
            setEnrolled(true)
            show("Enrolled", if (offline) "No network. Tap Verify." else "Now tap Verify.", R.color.ink)
        } else {
            show("Try again", describe(result.outcome), R.color.red)
        }
    }

    private fun onVerify() = runAction {
        val fp = faceping ?: return@runAction
        show("Turn your head", "Liveness check…", R.color.ink)
        // A live check: matches your face, asks you to turn your head (liveness), then matches again.
        val result = fp.verifyLive(personId, camera)
        val time = String.format(Locale.UK, "%.2f s", result.elapsed.inWholeMilliseconds / 1000.0)
        if (result.isMatch)
            show("Match", "Live face · $time · ${if (offline) "no network used" else "on the phone"}", R.color.green)
        else
            show(title(result.outcome), "Tap Verify to try again.", R.color.red)
    }

    private fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        enable(enrollButton, false)
        enable(verifyButton, false)
        lifecycleScope.launch {
            try {
                action()
            } catch (e: FacePingSetupException) {
                // The sandbox key is missing or revoked, or the very first run had no connection.
                camera.guideState = FaceGuideState.READY
                show(problemTitle(), e.message, R.color.red)
            } catch (e: FacePingModelException) {
                // The face models couldn't load on this phone: send people to your normal way in.
                camera.guideState = FaceGuideState.READY
                show("Face check unavailable", e.message, R.color.red)
            } catch (e: IllegalStateException) {
                // For example the camera didn't deliver a picture.
                camera.guideState = FaceGuideState.READY
                show("Try again", e.message, R.color.red)
            } finally {
                busy = false
                enable(enrollButton, true)
                enable(verifyButton, faceping?.enrolledIds?.contains(personId) == true)
            }
        }
    }

    // Fixed button colours override the disabled look, so fade them instead (as in the docs' picture).
    private fun enable(button: Button, on: Boolean) {
        button.isEnabled = on
        button.alpha = if (on) 1f else 0.45f
    }

    private fun setEnrolled(enrolled: Boolean) {
        enable(verifyButton, enrolled)
        enrollButton.setText(if (enrolled) R.string.enroll_again else R.string.enroll_my_face)
    }

    private fun show(title: String, note: String?, color: Int) {
        resultTitle.text = title
        resultTitle.setTextColor(ContextCompat.getColor(this, color))
        resultNote.text = note ?: ""
    }

    private fun setOffline(value: Boolean) {
        offline = value
        runOnUiThread { showNetwork() }
    }

    private fun showNetwork() {
        val off = offline
        network.setText(if (off) R.string.offline else R.string.online)
        network.setTextColor(ContextCompat.getColor(this, if (off) R.color.amber else R.color.slate))
        network.background.mutate().setTint(ContextCompat.getColor(this, if (off) R.color.pill_offline else R.color.pill))
    }

    private fun title(outcome: VerifyOutcome) = when (outcome) {
        VerifyOutcome.NO_MATCH -> "Not a match"
        VerifyOutcome.NOT_ENROLLED -> "Not enrolled"
        VerifyOutcome.NO_FACE -> "No face seen"
        VerifyOutcome.LIVENESS_FAILED -> "Not a live face"
        VerifyOutcome.CHALLENGE_FAILED -> "Head turn not seen"
        VerifyOutcome.EXPIRED -> "Enrolment expired"
        VerifyOutcome.LEASE_EXPIRED -> "Sync needed"
        VerifyOutcome.MATCH -> "Match"
    }

    private fun describe(outcome: EnrollOutcome) = when (outcome) {
        EnrollOutcome.NO_FACE -> "No face seen. Look at the camera."
        EnrollOutcome.MULTIPLE_FACES -> "More than one face. Just you, please."
        EnrollOutcome.FACE_TOO_SMALL -> "Move a little closer."
        EnrollOutcome.NOT_LIVE -> "That looked like a photo or a screen."
        EnrollOutcome.LIMIT_REACHED -> "The sandbox is full on this phone."
        EnrollOutcome.ENROLLED -> "Enrolled."
    }
}
