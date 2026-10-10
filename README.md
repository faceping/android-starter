# FacePing Android starter

**FaceCheck**: a small Android app, in Kotlin, that enrols your face and verifies it live, on the phone, with a head-turn liveness check. Switch on airplane mode and it still matches.

It's the finished app from the [Android quickstart](https://docs.faceping.ai/start/android/), built on [`ai.faceping:faceping-android`](https://central.sonatype.com/artifact/ai.faceping/faceping-android) from Maven Central.

## Run it

You need [Android Studio](https://developer.android.com/studio) (or JDK 17 or later and the Android SDK), and an Android phone (Android 7 or later, 64-bit) with USB debugging on and a cable. Emulators have no real front camera.

1. **Get a free sandbox key** at [app.faceping.ai/signup](https://app.faceping.ai/signup) (no card, no approval). In the dashboard: **Developers → Sandbox keys → Create sandbox key**.
2. **Paste it** into `app/build.gradle.kts`, replacing `fp_test_…`. (Or leave the file alone and pass it on the command line: `-Pfaceping.sandboxKey=fp_test_...`.)
3. **Run it** on your phone (press Run in Android Studio, or from a terminal). A debug build is fine: the FacePing core inside the SDK is already compiled for speed.

   ```bash
   ./gradlew installDebug
   ```

   Then open **FaceCheck** on the phone. In Android Studio, pick the `release` build variant and press Run.

4. Tap **Enrol my face**, then **Verify**: you should see **Match**. Now switch on airplane mode and tap **Verify** again.

Before you build your own app from this, change `applicationId` (and `namespace`) in `app/build.gradle.kts` from `com.example.facecheck` to your own, and sign release builds with your own key (the starter signs them with the debug key so they install straight away).

## What's where

| File | What it does |
|---|---|
| `app/build.gradle.kts` | Your sandbox key (the only place it's set), the FacePing dependency, the two ABIs FacePing runs on. |
| `app/src/main/kotlin/com/example/facecheck/FaceCheckApp.kt` | Turns FacePing on with your sandbox key (`FacePing.init`). |
| `app/src/main/res/layout/activity_main.xml` | The camera view (`ai.faceping.FaceCameraView`) and the two buttons. |
| `app/src/main/kotlin/com/example/facecheck/MainActivity.kt` | `enroll` and `verifyLive`, and what each result means. |
| `gradle/libs.versions.toml` | Versions, including `faceping`. |

## The sandbox

Free, for building and testing: up to 25 faces per device, each deleted 24 hours after it was enrolled. Faces never leave the phone. The app needs a connection the first time it runs, to activate your key; after that it works offline for 30 days.

To use FacePing with real people, go live from your [FacePing dashboard](https://app.faceping.ai). Your verify code doesn't change.

## Learn more

- [Android quickstart](https://docs.faceping.ai/start/android/): this app, step by step
- [How the SDK works](https://docs.faceping.ai/sdk/): liveness, storage, sandbox and production
- [Security](https://docs.faceping.ai/security/): the Rust core, encryption on the device
- Questions: support@faceping.ai

## Licence

This starter app is MIT licensed (see [LICENSE](LICENSE)): copy any of it into your own app. The FacePing SDK it uses has its own licence, the [FacePing SDK licence](https://faceping.ai/legal/sdk-licence), included in the AAR (`assets/faceping/LICENSE.txt`).
