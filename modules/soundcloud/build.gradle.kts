// The SoundCloud source module: user uploads in AAC up to 160 kbps, no account needed. Ported from ArchiveTune (github.com/4nx3b/ArchiveTune, GPL-3.0). Built into sora;
// written against :modules:api only, so it can later ship as its own APK.
plugins {
    alias(libs.plugins.kotlin.serialization)
    id("kotlin")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":modules:api"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
