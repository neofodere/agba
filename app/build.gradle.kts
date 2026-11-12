import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: localProperties.getProperty(name)

android {
    namespace = "dev.neofodere.agba"
    compileSdk = 37
    ndkVersion = "28.0.13004108"

    defaultConfig {
        applicationId = "dev.neofodere.agba"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("agbaVersionCode").map { it.toInt() }.getOrElse(1)
        versionName = providers.gradleProperty("agbaVersionName").getOrElse("1.0.0")
        resValue("string", "build_number", providers.gradleProperty("agbaBuild").getOrElse("0"))
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        val keystore = signingValue("AGBA_KEYSTORE")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = signingValue("AGBA_KEYSTORE_PASSWORD")
                keyAlias = signingValue("AGBA_KEY_ALIAS")
                keyPassword = signingValue("AGBA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.kotlin.coroutines)
}
