// The ArchiveTune module engine, ported whole from github.com/rukamori/ArchiveTune (GPL-3.0):
// the .arch package reader, the QuickJS provider runtime, the sandboxed HTTP client, the
// per-installation encrypted storage and the catalog reader. Self-contained on purpose — no
// dependency on the app — so it stays a faithful copy of upstream.
plugins {
    // the same id-without-version shape the providers use; the buildscript classpath carries the version
    id("com.android.library")
}

android {
    namespace = "moe.rukamori.archivetune.modules"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation(libs.okhttp)
    implementation(libs.quickjs.kt)
    implementation(libs.jsoup)
}
