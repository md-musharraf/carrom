plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

/**
 * Online configuration comes from Gradle properties (e.g. in ~/.gradle/gradle.properties or with
 * -P on the command line) so no keys are committed:
 *   carrom.apiBaseUrl          backend URL (default: the emulator's view of the host machine)
 *   carrom.googleWebClientId   OAuth *web* client ID used to request Google ID tokens
 *   carrom.facebookAppId / carrom.facebookClientToken   Facebook Login credentials
 */
fun onlineProperty(name: String, default: String = ""): String =
    providers.gradleProperty("carrom.$name").orElse(default).get()

android {
    namespace = "com.example.royalcarromclassic"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.royalcarromclassic"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "API_BASE_URL", "\"${onlineProperty("apiBaseUrl", "http://10.0.2.2:8080")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${onlineProperty("googleWebClientId")}\"")
        val facebookAppId = onlineProperty("facebookAppId", "0")
        resValue("string", "facebook_app_id", facebookAppId)
        resValue("string", "fb_login_protocol_scheme", "fb$facebookAppId")
        resValue("string", "facebook_client_token", onlineProperty("facebookClientToken", "unset"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
      resValues = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    // JVM unit tests build ViewModels with a plain Application and log through android.util.Log.
    testOptions {
      unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        ignoreWarnings = false
        warningsAsErrors = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Security - Encrypted SharedPreferences
  implementation(libs.androidx.security.crypto)

  // Online: REST, realtime and JSON
  implementation(libs.okhttp)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.socket.io.client) {
    exclude(group = "org.json", module = "json") // Provided by the Android platform.
  }

  // Sign-in providers
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.google.id)
  implementation(libs.facebook.login)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.org.json) // Real org.json for JVM tests (android.jar only has stubs).

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}

tasks.register<Copy>("installGitHooks") {
    from(rootProject.file(".githooks"))
    into(rootProject.file(".git/hooks"))
}
