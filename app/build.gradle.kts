plugins {
    alias(libs.plugins.android.application)
}

// Your sandbox key: app.faceping.ai, Developers, Sandbox keys, Create sandbox key. Paste it here, replacing fp_test_…
// This is the only place it's set. To try a key without putting it in a file, pass it to Gradle instead:
//   ./gradlew installDebug -Pfaceping.sandboxKey=fp_test_...   (or set faceping.sandboxKey in ~/.gradle/gradle.properties)
val sandboxKey: String = providers.gradleProperty("faceping.sandboxKey").getOrElse("fp_test_…")

// Check-in device mode: a device token (fpd_…, from your practice list's Devices tab or POST /v1/lists/{listId}/devices)
// and the person to verify (their personId). With a device token, the app downloads that list's people instead of
// enrolling on the phone, and the sandbox key is ignored. Set them here or pass them to Gradle:
//   ./gradlew installRelease -Pfaceping.deviceToken=fpd_... -Pfaceping.personId=ACME-000123
val deviceToken: String = providers.gradleProperty("faceping.deviceToken").getOrElse("")
val personId: String = providers.gradleProperty("faceping.personId").getOrElse("me")

android {
    namespace = "com.yourcompany.facecheck"
    compileSdk = 36

    defaultConfig {
        // Change this to your own application ID before you ship anything.
        applicationId = "com.yourcompany.facecheck"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SANDBOX_KEY", "\"$sandboxKey\"")
        buildConfigField("String", "DEVICE_TOKEN", "\"$deviceToken\"")
        buildConfigField("String", "PERSON_ID", "\"$personId\"")
        // FacePing's native core is built for these two ABIs (phones and emulators).
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // A starter: release builds are signed with the debug key so they install on your phone.
            // Use your own signing config before you publish an app.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.faceping)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.coroutines.android)
}
