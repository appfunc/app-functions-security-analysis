import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(FileInputStream(file))
}

fun localProp(key: String, default: String = ""): String =
    localProperties.getProperty(key) ?: default

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.filipfan.appfunctionspilot.tool"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.filipfan.appfunctionspilot.tool"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "T4_URL", "\"${localProp("T4_URL")}\"")
        buildConfigField("boolean", "T1_ENABLED", localProp("T1_ENABLED", "false"))
        buildConfigField("boolean", "T2_ENABLED", localProp("T2_ENABLED", "false"))
        buildConfigField("boolean", "T3_ENABLED", localProp("T3_ENABLED", "false"))
        buildConfigField("boolean", "T4_ENABLED", localProp("T4_ENABLED", "false"))
        buildConfigField("boolean", "T5_ENABLED", localProp("T5_ENABLED", "false"))
        buildConfigField("boolean", "T6_ENABLED", localProp("T6_ENABLED", "false"))
        buildConfigField("boolean", "T7_ENABLED", localProp("T7_ENABLED", "false"))
    }


    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

// KSP (Kotlin Symbol Processing) configurations for app functions code generation.
ksp {
    arg("appfunctions:aggregateAppFunctions", "true")
    arg("appfunctions:generateMetadataFromSchema", "true")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.gson)
    implementation(libs.androidx.appfunctions)
    implementation(libs.appfunctions.service)
    implementation(libs.okhttp)
    ksp(libs.appfunctions.compiler)
}
