import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
}

// Load secrets from local.properties (NOT checked into git).
// Required keys:
//   ANGELONE_API_KEY=<your Angel One Smart API key>
val secretsProperties = Properties().apply {
    val secretsFile = rootProject.file("local.properties")
    if (secretsFile.exists()) {
        load(FileInputStream(secretsFile))
    }
}

fun secret(key: String, default: String = ""): String =
    secretsProperties.getProperty(key) ?: System.getenv(key) ?: default

android {
    namespace = "com.example.personalfinancemanager"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.personalfinancemanager"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // BuildConfig fields — secrets injected at build time, never committed.
        buildConfigField(
            "String",
            "ANGELONE_API_KEY",
            "\"${secret("ANGELONE_API_KEY", "REPLACE_ME")}\""
        )

        // Certificate pin for Angel One API. Compute with:
        //   openssl s_client -servername apiconnect.angelbroking.com \
        //     -connect apiconnect.angelbroking.com:443 < /dev/null 2>/dev/null \
        //     | openssl x509 -pubkey -noout \
        //     | openssl pkey -pubin -outform der \
        //     | openssl dgst -sha256 -binary | openssl enc -base64
        // Paste the resulting "sha256/<base64>" string here.
        buildConfigField(
            "String",
            "ANGELONE_CERT_PIN",
            "\"${secret("ANGELONE_CERT_PIN", "")}\""
        )

        // ---- Upstox OAuth2 ----
        // Obtain these by registering an app at https://account.upstox.com/developer/apps
        // and pasting the generated values into local.properties. The redirect URI
        // in the developer portal MUST exactly match UPSTOX_REDIRECT_URI below and
        // match the <data> element in UpstoxOAuthCallbackActivity's intent-filter.
        buildConfigField(
            "String",
            "UPSTOX_CLIENT_ID",
            "\"${secret("UPSTOX_CLIENT_ID", "")}\""
        )
        buildConfigField(
            "String",
            "UPSTOX_CLIENT_SECRET",
            "\"${secret("UPSTOX_CLIENT_SECRET", "")}\""
        )
        buildConfigField(
            "String",
            "UPSTOX_REDIRECT_URI",
            "\"${secret("UPSTOX_REDIRECT_URI", "https://wealthflow.app/callback")}\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Lifecycle (ViewModel + LiveData)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // Encrypted credentials storage
    implementation(libs.security.crypto)

    // Room Database
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")
    implementation("androidx.room:room-common:$room_version")

    // WorkManager (daily budget checks)
    implementation("androidx.work:work-runtime:2.9.1")

    // Charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // 2FA
    implementation("dev.samstevens.totp:totp:1.7.1")

    // PDF Parsing
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // ViewPager2
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // ---- Unit tests ----
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.core.testing)

    // ---- Instrumentation tests ----
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.core.testing)
}
