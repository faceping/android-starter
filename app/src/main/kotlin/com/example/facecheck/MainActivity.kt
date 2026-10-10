package com.example.facecheck

import ai.faceping.EnrolOutcome
import ai.faceping.FaceCameraView
import ai.faceping.FaceGuideState
import ai.faceping.FacePing
import ai.faceping.FacePingModelException
import ai.faceping.FacePingSetupException
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

/** The same screen as the MAUI FaceCheck sample: enrol your face, then verify it with a live check, with or without a network. */
class MainActivity : ComponentActivity() {
    private lateinit var camera: FaceCameraView
    private lateinit var enrolButton: Button
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
        enrolButton = findViewById(R.id.enrol)
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
        enrolButton.setOnClickListener { onEnrol() }
        verifyButton.setOnClickListener { onVerify() }

        connectivity = getSystemService(ConnectivityManager::class.java)
        offline = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true
        showNetwork()
        connectivity.registerDefaultNetworkCallback(networkCallback)

        faceping = try {
            FacePing.current
        } catch (e: IllegalStateException) {
            show("Sandbox key problem", "Set your sandbox key in app/build.gradle.kts (it starts with fp_test_).", R.color.red)
            enable(enrolButton, false)
            null
        }

        // A face enrolled earlier may still be on the phone (sandbox faces are deleted 24 hours after enrolment).
        lifecycleScope.launch {
            val fp = faceping ?: return@launch
            try {
                fp.sync()
            } catch (e: FacePingSetupException) {
                show("Sandbox key problem", e.message, R.color.red)
            } catch (e: FacePingModelException) {
                show("Face check unavailable", e.message, R.color.red)
            }
            setEnrolled("me" in fp.enrolledIds)
        }
    }

    override fun onDestroy() {
        connectivity.unregisterNetworkCallback(networkCallback)
        super.onDestroy()
    }

    private fun onEnrol() = runAction {
        val fp = faceping ?: return@runAction
        show("Hold still", "Taking the photo…", R.color.ink)
        camera.guideState = FaceGuideState.WORKING
        val photo = camera.capture()
        val result = fp.enrol("me", photo)
        camera.guideState = FaceGuideState.READY
        if (result.outcome == EnrolOutcome.ENROLLED) {
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
        val result = fp.verifyLive("me", camera)
        val time = String.format(Locale.UK, "%.2f s", result.elapsed.inWholeMilliseconds / 1000.0)
        if (result.isMatch)
            show("Match", "Live face · $time · ${if (offline) "no network used" else "on the phone"}", R.color.green)
        else
            show(title(result.outcome), "Tap Verify to try again.", R.color.red)
    }

    private fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        enable(enrolButton, false)
        enable(verifyButton, false)
        lifecycleScope.launch {
            try {
                action()
            } catch (e: FacePingSetupException) {
                // The sandbox key is missing or revoked, or the very first run had no connection.
                camera.guideState = FaceGuideState.READY
                show("Sandbox key problem", e.message, R.color.red)
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
                enable(enrolButton, true)
                enable(verifyButton, faceping?.enrolledIds?.contains("me") == true)
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
        enrolButton.setText(if (enrolled) R.string.enrol_again else R.string.enrol_my_face)
    }

    private fun show(title: String, note: String?, colour: Int) {
        resultTitle.text = title
        resultTitle.setTextColor(ContextCompat.getColor(this, colour))
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

    private fun describe(outcome: EnrolOutcome) = when (outcome) {
        EnrolOutcome.NO_FACE -> "No face seen. Look at the camera."
        EnrolOutcome.MULTIPLE_FACES -> "More than one face. Just you, please."
        EnrolOutcome.FACE_TOO_SMALL -> "Move a little closer."
        EnrolOutcome.NOT_LIVE -> "That looked like a photo or a screen."
        EnrolOutcome.LIMIT_REACHED -> "The sandbox is full on this phone."
        EnrolOutcome.ENROLLED -> "Enrolled."
    }
}
