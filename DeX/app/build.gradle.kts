plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

android {
    namespace = "com.dexstudios.dex"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.dexstudios.dex"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        // Google Sign-In server (OAuth "Web application") client ID used as the
        // Credential Manager serverClientId / ID-token audience. The Android OAuth
        // client is matched automatically via package + SHA-1 — do NOT put the
        // Android client ID here. Configure via -PGOOGLE_SIGN_IN_CLIENT_ID="..."
        // or gradle.properties; blank = sign-in hidden.
        val googleClientId = providers.gradleProperty("GOOGLE_SIGN_IN_CLIENT_ID").orNull ?: ""
        buildConfigField("String", "GOOGLE_SIGN_IN_CLIENT_ID", "\"$googleClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/NOTICE.txt"
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" +
                layout.buildDirectory.get().asFile.absolutePath + "/compose_metrics",
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:stabilityConfigurationPath=" +
                rootProject.file("compose_stability.conf").absolutePath
        )
    }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.koin.androidx.compose)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material3.windowSizeClass)

  // Liquid Glass Backdrop
  implementation(libs.backdrop)

  // Lottie Animation Engine
  implementation(libs.lottie.compose)

  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockk)
  testImplementation(libs.koin.test.junit4)
  testImplementation(libs.ktor.client.mock)
  testImplementation(libs.json.org)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.mockk.android)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Serialization
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.ktor.serialization.kotlinx.json)

  // OkHttp (WebSocket client)
  implementation(libs.okhttp)

  // Cronet (HTTP/3 + QUIC) — backed by Google Play Services system Chromium engine (-15MB APK size)
  implementation(libs.play.services.cronet) {
      exclude(group = "org.chromium.net", module = "cronet-shared")
  }

    // Storage
    implementation(libs.androidx.datastore.preferences)

    // Logging
    implementation(libs.timber)

  // Ktor Client
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.cio)
  implementation(libs.ktor.client.content.negotiation)

  // WorkManager
  implementation(libs.androidx.work.runtime.ktx)

  // Koin
  implementation(libs.koin.android)

  // QR Scanner
  implementation(libs.play.services.code.scanner)

  // Google Sign-In (verified email identity)
  implementation(libs.play.services.auth)

  // Image & Video Thumbnail Loading
  implementation(libs.coil.compose)
  implementation(libs.coil.video)
  implementation(libs.coil.network.okhttp)

  // Baseline Profiles (AOT compilation for cold-start perf)
  implementation(libs.androidx.profileinstaller)

  // Credential Manager
  implementation(libs.androidx.identity.core)
  implementation(libs.androidx.identity.play)
  implementation(libs.androidx.identity.googleid)

  // AppFunctions
  implementation(libs.androidx.appfunctions)
  ksp(libs.androidx.appfunctions.compiler)
}
