import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

// Release signing is loaded from environment variables (CI) or from a
// git-ignored keystore.properties file (local builds). The environment wins.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
  if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { load(it) }
  }
}

val signingValue: (String, String) -> String? = { envName, propertyName ->
  System.getenv(envName)?.takeIf { it.isNotBlank() }
    ?: keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
}

val releaseStorePath = signingValue("KEYSTORE_PATH", "storeFile")
val releaseStorePassword = signingValue("STORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("KEY_PASSWORD", "keyPassword")
val releaseKeystoreFile = releaseStorePath?.let { rootProject.file(it) }
val hasFullReleaseSigning = listOf(
  releaseStorePath,
  releaseStorePassword,
  releaseKeyAlias,
  releaseKeyPassword
).all { it != null }

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  val envVersionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 110
  val envVersionName = System.getenv("APP_VERSION_NAME") ?: "1.0.110"

  defaultConfig {
    applicationId = "apps.feather.browser"
    minSdk = 24
    targetSdk = 36
    versionCode = envVersionCode
    versionName = envVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("releaseConfig") {
      if (hasFullReleaseSigning) {
        storeFile = releaseKeystoreFile
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      // Never fall back to a debug or unsigned build for release.
      signingConfig = if (hasFullReleaseSigning) {
        signingConfigs.getByName("releaseConfig")
      } else {
        null
      }
    }
    debug {
      // Uses Android Gradle Plugin's built-in debug signing config, which
      // creates ~/.android/debug.keystore on demand. Debug keys are never
      // used for release builds.
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
}

// Fail before packaging if a release is requested without a complete,
// existing signing configuration. This prevents accidentally publishing an
// unsigned or debug-signed APK.
val releasePackagingTasks = setOf(
  "packageRelease",
  "packageReleaseBundle",
  "signReleaseBundle"
)

tasks.matching { it.name in releasePackagingTasks }.configureEach {
  doFirst {
    if (!hasFullReleaseSigning) {
      val missing = buildList {
        if (releaseStorePath == null) add("KEYSTORE_PATH (or storeFile)")
        if (releaseStorePassword == null) add("STORE_PASSWORD (or storePassword)")
        if (releaseKeyAlias == null) add("KEY_ALIAS (or keyAlias)")
        if (releaseKeyPassword == null) add("KEY_PASSWORD (or keyPassword)")
      }
      throw GradleException(
        "Release signing is not configured. Missing: ${missing.joinToString()}. " +
          "Set the environment variables or create keystore.properties from " +
          "keystore.properties.example. Refusing to produce an unsigned or debug-signed release build."
      )
    }
    if (releaseKeystoreFile == null || !releaseKeystoreFile.exists()) {
      throw GradleException(
        "Release keystore not found at ${releaseKeystoreFile?.absolutePath ?: "(unset)"}. " +
          "Check KEYSTORE_PATH or storeFile in keystore.properties."
      )
    }
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.webkit)
  // implementation(libs.androidx.swiperefreshlayout)
  implementation(libs.androidx.media)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)

  // androidTestImplementation(platform(libs.androidx.compose.bom))
  // androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  // androidTestImplementation(libs.androidx.espresso.core)
  // androidTestImplementation(libs.androidx.junit)
  // androidTestImplementation(libs.androidx.runner)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)

  "ksp"(libs.androidx.room.compiler)
}
