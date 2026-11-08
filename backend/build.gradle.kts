import org.gradle.api.GradleException
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.devtools.ksp")

}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

// Fail fast with a clear message instead of silently baking the literal string
// "null" into the APK (the previous behaviour when local.properties was absent).
fun requiredLocalProperty(name: String): String {
    val value = localProperties.getProperty(name)
    if (value.isNullOrBlank()) {
        throw GradleException(
            "$name is missing from local.properties. Copy local.properties.example " +
                "to local.properties and fill in the values (see README → Building locally)."
        )
    }
    return value
}

android {
    namespace = "nethical.questphone.backend"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")


        buildConfigField("String", "SUPABASE_URL", "\"${requiredLocalProperty("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_API_KEY", "\"${requiredLocalProperty("SUPABASE_API_KEY")}\"")
        // Legacy field — not referenced anywhere in code, so it stays optional
        buildConfigField("String", "API_URL", "\"${localProperties.getProperty("API_URL") ?: ""}\"")
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
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.hilt.common)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.ktor.client.okhttp)
    implementation (libs.guava)
    implementation (libs.kotlinx.serialization.json)

    implementation (libs.androidx.work.runtime.ktx)
    implementation(project(":data"))
    implementation(project(":core"))

}