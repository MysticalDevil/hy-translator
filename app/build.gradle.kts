plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.devil.hytranslator"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.devil.hytranslator"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/.keystores/hy-translator-release.jks")
            storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String?
            keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String?
            keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
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

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    sourceSets {
      getByName("main") {
        jniLibs.directories.add(
            rootProject.layout.projectDirectory.dir("third_party/sherpa-onnx-android/jniLibs")
                .asFile
                .absolutePath,
        )
        jniLibs.directories.add(
            rootProject.layout.projectDirectory.dir("third_party/paddle-lite-android/jniLibs")
                .asFile
                .absolutePath,
        )
      }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.register("verifySherpaOnnxRuntime") {
    group = "verification"
    description = "Checks that the local sherpa-onnx Android runtime jniLibs are installed."

    val runtimeDir = rootProject.layout.projectDirectory.dir("third_party/sherpa-onnx-android/jniLibs")
    val requiredAbis = listOf("arm64-v8a", "x86_64")
    val requiredLibs = listOf("libsherpa-onnx-jni.so", "libonnxruntime.so")

    inputs.dir(runtimeDir).optional()

    doLast {
        val missing = requiredAbis.flatMap { abi ->
            requiredLibs.mapNotNull { lib ->
                val file = runtimeDir.file("$abi/$lib").asFile
                if (file.isFile) null else file
            }
        }
        check(missing.isEmpty()) {
            "Missing sherpa-onnx runtime files:\n" +
                missing.joinToString(separator = "\n") { " - ${it.path}" } +
                "\nRun scripts/setup-sherpa-onnx-android.sh"
        }
    }
}

tasks.register("verifyPaddleLiteRuntime") {
    group = "verification"
    description = "Checks that the local Paddle Lite Android runtime is installed."

    val runtimeDir = rootProject.layout.projectDirectory.dir("third_party/paddle-lite-android")
    val requiredFiles = listOf(
        "PaddlePredictor.jar",
        "jniLibs/arm64-v8a/libpaddle_lite_jni.so",
        "jniLibs/arm64-v8a/libc++_shared.so",
    )

    inputs.dir(runtimeDir).optional()

    doLast {
        val missing = requiredFiles.mapNotNull { relativePath ->
            val file = runtimeDir.file(relativePath).asFile
            if (file.isFile) null else file
        }
        check(missing.isEmpty()) {
            "Missing Paddle Lite runtime files:\n" +
                missing.joinToString(separator = "\n") { " - ${it.path}" } +
                "\nRun scripts/setup-paddle-lite-android.sh"
        }
    }
}

dependencies {
  implementation(project(":lib"))

  val paddlePredictorJar = rootProject.layout.projectDirectory
      .file("third_party/paddle-lite-android/PaddlePredictor.jar")
      .asFile
  if (paddlePredictorJar.isFile) {
      implementation(files(paddlePredictorJar))
  }

  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.datastore.preferences)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  debugImplementation(libs.androidx.compose.ui.tooling)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  implementation(libs.okhttp)

  // ML Kit OCR
  implementation(libs.text.recognition.chinese)

  // CameraX
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}
