plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "io.github.hyperkey"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.hyperkey"
        minSdk = 29
        targetSdk = 36
        versionCode = 4
        versionName = "0.2.1"
    }
    buildFeatures { compose = true; buildConfig = true }
    bundle { language { enableSplit = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*") }
}
dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("org.jetbrains.compose.runtime:runtime:1.10.2")
    implementation("org.jetbrains.compose.foundation:foundation:1.10.2")
    implementation("top.yukonga.miuix.kmp:miuix:0.8.7")
    testImplementation("junit:junit:4.13.2")
}
