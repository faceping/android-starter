# FacePing Android starter

**FaceCheck**: a small Android app, in Kotlin, that enrols your face and verifies it live, on the phone, with a head-turn liveness check. Switch on airplane mode and it still matches.

It's a fuller version of the app you build in the [Android guide](https://docs.faceping.ai/start/android/) (it also runs as a check-in device and shows what each sync found), built on [`ai.faceping:faceping-android`](https://central.sonatype.com/artifact/ai.faceping/faceping-android) from Maven Central.

## Run it

You need [Android Studio](https://developer.android.com/studio) (or JDK 17 or later and the Android SDK), and an Android phone (Android 7 or later, 64-bit) with USB debugging on and a cable. Use a phone: an emulator only has a front camera if you map your computer's webcam to it, which is slower and less reliable for the liveness check.

1. **Get a free sandbox key** at [app.faceping.ai/signup](https://app.faceping.ai/signup) (no card, no approval). In the dashboard: **Developers → Sandbox keys → Create sandbox key**.
2. **Paste it** into `app/build.gradle.kts`, replacing `fp_test_…`. (Or leave the file alone and pass it on the command line: `-Pfaceping.sandboxKey=fp_test_...`.)
3. **Run it** on your phone. Use the `release` build variant, as the guide does, for real timings (the starter signs release builds with the debug key, so they install straight away). In Android Studio, pick the `release` build variant and press Run; or from a terminal:

   ```bash
   ./gradlew installRelease
   ```

   Then open **FaceCheck** on the phone.

4. Tap **Enrol my face**, then **Verify**: you should see **Match**. Now switch on airplane mode and tap **Verify** again.

Before you build your own app from this, change `applicationId` (and `namespace`) in `app/build.gradle.kts` from `com.yourcompany.facecheck` to your own, and sign release builds with your own key.

## Try it as a check-in device

Once you've tried the server side with your sandbox key (a practice check-in list, an enrolment link, a device), this app can be the check-in device:

1. In the dashboard, open your practice list's **Devices** tab and add a device (or call `POST /v1/lists/{listId}/devices`). Copy the `fpd_` token.
2. Set the token and the person to verify (the `personId` you enrolled on the practice list, through an enrolment link or from your server), in `app/build.gradle.kts` or on the command line:

   ```bash
   ./gradlew installRelease -Pfaceping.deviceToken=fpd_yourtoken -Pfaceping.personId=ACME-000123
   ```

   With a device token set, the sandbox key is ignored. To keep the sandbox app on the phone as well, also change `applicationId` (for example to `com.yourcompany.facecheck.device`).
3. Open it. The app downloads the list, hides **Enrol my face** (a check-in device gets its people from the server) and opens **Verify** once that person is on the phone. After each sync it says what it found: how many people, or that the token isn't valid, the list has finished, or it couldn't connect.

See [Devices and offline sync](https://docs.faceping.ai/devices/).

## What's where

| File | What it does |
|---|---|
| `app/build.gradle.kts` | Your sandbox key, or a device token and person id (the only place they're set), the FacePing dependency, the two ABIs FacePing runs on. |
| `app/src/main/kotlin/com/yourcompany/facecheck/FaceCheckApp.kt` | Turns FacePing on with your sandbox key or device token (`FacePing.init`). |
| `app/src/main/res/layout/activity_main.xml` | The camera view (`ai.faceping.FaceCameraView`) and the two buttons. |
| `app/src/main/kotlin/com/yourcompany/facecheck/MainActivity.kt` | `enroll`, `verifyLive` and `sync`, and what each result means. |
| `app/src/main/res/xml/data_extraction_rules.xml`, `backup_rules.xml` | Keep FacePing's face store (`files/faceping/`) and its wrapped key (`faceping.keys.xml`) out of backups and transfers to a new phone: the key can't work on another device. |
| `gradle/libs.versions.toml` | Versions, including `faceping`. |

## The sandbox

Free, for building and testing: up to 25 faces per device, each deleted 24 hours after it was enrolled. Faces you enrol in the app never leave the phone. People on your practice list (see above) are held by FacePing, each for 24 hours. The app needs a connection the first time it runs, to activate your key; after that it works offline for 30 days.

To use FacePing with real people, go live from your [FacePing dashboard](https://app.faceping.ai). Your verify code doesn't change.

## Learn more

- [Get started with Android](https://docs.faceping.ai/start/android/): the app, step by step
- [How the SDK works](https://docs.faceping.ai/sdk/): liveness, storage, sandbox and production
- [Security](https://docs.faceping.ai/security/): the Rust core, encryption on the device
- Questions: support@faceping.ai

## Licence

This starter app is MIT licensed (see [LICENSE](LICENSE)): copy any of it into your own app. The FacePing SDK it uses has its own licence, the [FacePing SDK licence](https://faceping.ai/legal/sdk-licence), included in the AAR (`assets/faceping/LICENSE.txt`).
