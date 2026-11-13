import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Signing credentials live in local.properties (git-ignored). Without them a
// release build falls back to the debug key so the project still builds.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Local TTS bootstrap is debug-only; the release variant gets empty strings so a
// key typed into local.properties can never be compiled into a published APK.
val ttsKeys = listOf("endpoint", "protocol", "model", "voice", "instructions", "key")

// Local-only switch for a TTS test APK: ./gradlew :app:assembleRelease -PttsLocal
// Normal release builds (and CI) keep every TTS field empty.
val ttsLocal = project.hasProperty("ttsLocal")

fun String.buildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

android {
    namespace = "dev.wordloom"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.wordloom"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "5.0.2"
    }

    signingConfigs {
        create("release") {
            val storePath = localProps.getProperty("wordloom.storeFile")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = localProps.getProperty("wordloom.storePassword")
                keyAlias = localProps.getProperty("wordloom.keyAlias")
                keyPassword = localProps.getProperty("wordloom.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            ttsKeys.forEach { key ->
                buildConfigField(
                    "String",
                    "TTS_${key.uppercase()}",
                    localProps.getProperty("wordloom.tts.$key").orEmpty().buildConfigString(),
                )
            }
            // Debug is arm64-only too; the x86 emulator is not used on this
            // machine, and four ABI copies made every debug APK 122 MiB.
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
        release {
            ttsKeys.forEach { key ->
                buildConfigField(
                    "String",
                    "TTS_${key.uppercase()}",
                    if (ttsLocal) {
                        localProps.getProperty("wordloom.tts.$key").orEmpty().buildConfigString()
                    } else {
                        "\"\""
                    },
                )
            }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (localProps.getProperty("wordloom.storeFile") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            // Every build type is arm64-only: MapLibre and ML Kit ship native
            // code per ABI, and bundling four copies took the APK from ~38 MiB
            // to 122 MiB for nothing — a phone only ever loads one. If an x86
            // emulator is ever needed again, remove the filter for that build.
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jsoup)
    implementation(libs.maplibre)
    implementation(libs.mlkit.translate)
    implementation(libs.play.services.tasks)
    implementation(libs.haze)
    implementation(libs.compose.shimmer)

    testImplementation(libs.junit)
}
