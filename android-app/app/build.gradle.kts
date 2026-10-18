plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.novelarrow.reader"
    compileSdk = 36
    val appVersionName = providers.environmentVariable("APP_VERSION_NAME")
        .orElse(providers.gradleProperty("appVersionName"))
        .getOrElse("3.10.1")

    defaultConfig {
        applicationId = "com.novelarrow.reader"
        minSdk = 26
        targetSdk = 36
        versionCode = 38
        versionName = appVersionName

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/release.keystore")
            storePassword = providers.environmentVariable("KEYSTORE_PASSWORD")
                .orElse(providers.gradleProperty("KEYSTORE_PASSWORD"))
                .getOrElse("novelarrow")
            keyAlias = providers.environmentVariable("KEY_ALIAS")
                .orElse(providers.gradleProperty("KEY_ALIAS"))
                .getOrElse("novelarrow")
            keyPassword = providers.environmentVariable("KEY_PASSWORD")
                .orElse(providers.gradleProperty("KEY_PASSWORD"))
                .getOrElse("novelarrow")
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    androidResources {
        noCompress += listOf("onnx", "ort", "bin")
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

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation("org.json:json:20240303")

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Media3 & Background Audio
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.common)

  // Icons, File Management, Storage & HTML Parsing
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.documentfile)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.jsoup)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.markwon.core)
  implementation(libs.jump3r)
}
