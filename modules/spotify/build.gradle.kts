// The Spotify source module: Spotify's own catalog streamed from its CDN the way the web player
// does it, ported from Stopify (github.com/Anattana-Labs/Stopify — see its README for the
// protocol walk). Written against :modules:api only, like the other built-in sources.
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
    // the dealer session waits for the manifest on a deferred, timed out per song (bridge's arrangement)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
