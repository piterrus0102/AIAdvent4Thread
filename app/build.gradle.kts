import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.cio)
            
            // Room Database
            implementation(libs.room.runtime)
            implementation(libs.room.ktx)
            
            // Koin
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)
        }
        
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            
            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            
            // Kotlinx Serialization
            implementation(libs.kotlinx.serialization.json)
            
            // Koin Core
            implementation(libs.koin.core)
        }
        
        commonTest.dependencies {
            implementation(libs.junit)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
}

android {
    namespace = "ru.piterrus.aiadvent4thread"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.piterrus.aiadvent4thread"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Читаем секреты из local.properties
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            FileInputStream(localPropertiesFile).use { properties.load(it) }
        }
        
        // Добавляем как BuildConfig поля (для использования в коде)
        val apiKey = properties.getProperty("yandex.api.key") ?: ""
        val folderId = properties.getProperty("yandex.folder.id") ?: ""
        val hfToken = properties.getProperty("huggingface.token") ?: ""
        
        buildConfigField("String", "YANDEX_API_KEY", "\"$apiKey\"")
        buildConfigField("String", "YANDEX_FOLDER_ID", "\"$folderId\"")
        buildConfigField("String", "HUGGINGFACE_TOKEN", "\"$hfToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
}