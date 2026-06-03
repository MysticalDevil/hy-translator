plugins {
    alias(libs.plugins.android.library)
}

val llamaPrebuiltDir = layout.projectDirectory.dir("src/main/prebuilt")
val llamaPrebuiltAvailableAtConfiguration = llamaPrebuiltDir.asFile.exists()
val forceLlamaPrebuiltSync = providers.gradleProperty("forceLlamaPrebuiltSync")
    .map(String::toBoolean)
    .orElse(false)
val llamaPrebuiltLibraryNames = setOf(
    "libggml.so",
    "libggml-base.so",
    "libggml-cpu.so",
    "libllama.so",
    "libllama-common.so",
    "libomp.so",
)
val syncLlamaPrebuiltLibs by tasks.registering {
    description = "Caches llama.cpp native libraries for reuse by later Android builds."
    group = "build"
    inputs.dir(layout.buildDirectory.dir("intermediates/cxx"))
    outputs.dir(llamaPrebuiltDir)
    onlyIf {
        forceLlamaPrebuiltSync.get() || !llamaPrebuiltAvailableAtConfiguration
    }

    doLast {
        val cxxDir = layout.buildDirectory.dir("intermediates/cxx").get().asFile
        if (!cxxDir.isDirectory) return@doLast

        val newestLibraries = mutableMapOf<Pair<String, String>, File>()
        cxxDir.walkTopDown()
            .filter { candidate ->
                candidate.isFile &&
                    candidate.length() > 0L &&
                    candidate.name in llamaPrebuiltLibraryNames
            }
            .forEach { candidate ->
                val segments = candidate.relativeTo(cxxDir).invariantSeparatorsPath.split("/")
                val objIndex = segments.indexOf("obj")
                if (objIndex < 0 || objIndex + 1 >= segments.size) return@forEach

                val abi = segments[objIndex + 1]
                val key = abi to candidate.name
                val previous = newestLibraries[key]
                if (previous == null || candidate.lastModified() > previous.lastModified()) {
                    newestLibraries[key] = candidate
                }
            }

        newestLibraries.forEach { (key, sourceFile) ->
            val (abi, libraryName) = key
            val outputDir = llamaPrebuiltDir.dir(abi).asFile
            outputDir.mkdirs()
            sourceFile.copyTo(File(outputDir, libraryName), overwrite = true)
        }
    }
}

android {
    namespace = "com.arm.aichat"
    compileSdk = 37

    ndkVersion = "29.0.13113456"

    defaultConfig {
        minSdk = 33

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
             abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DCMAKE_MESSAGE_LOG_LEVEL=DEBUG"
                arguments += "-DCMAKE_VERBOSE_MAKEFILE=ON"

                arguments += "-DBUILD_SHARED_LIBS=ON"
                arguments += "-DLLAMA_BUILD_APP=OFF"
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DLLAMA_OPENSSL=OFF"

                arguments += "-DGGML_NATIVE=OFF"
                arguments += "-DGGML_BACKEND_DL=OFF"
                arguments += "-DGGML_CPU_ALL_VARIANTS=OFF"
                arguments += "-DGGML_LLAMAFILE=OFF"
                arguments += "-DLLAMA_PREBUILT_DIR=${llamaPrebuiltDir.asFile.absolutePath}"
                arguments += "-DLLAMA_ALLOW_PREBUILT=$llamaPrebuiltAvailableAtConfiguration"
            }
        }
        aarMetadata {
            minCompileSdk = 35
        }
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.setSrcDirs(listOf(llamaPrebuiltDir.asFile))
        }
    }
}

val buildCMakeTasks = tasks.matching { task ->
    task.name.startsWith("buildCMake")
}

buildCMakeTasks.configureEach {
    finalizedBy(syncLlamaPrebuiltLibs)
}

syncLlamaPrebuiltLibs.configure {
    mustRunAfter(buildCMakeTasks)
}

tasks.matching { task ->
    task.name.matches(Regex("merge.*JniLibFolders"))
}.configureEach {
    dependsOn(syncLlamaPrebuiltLibs)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
