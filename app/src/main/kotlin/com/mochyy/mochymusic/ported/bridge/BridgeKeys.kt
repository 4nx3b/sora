// The preference keys the vendored Neiro styles read. Where sora has the same setting the key is sora's own
// (translate lyrics, translation language), so the ported styles follow the user's settings. The rest are
// Neiro-only and stored under a "ported_" prefix so they cannot collide with a sora setting.

package com.mochyy.mochymusic.ported.bridge

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

// Neiro romanizes with an AI model; sora romanizes per language in its own lyrics renderer (LyricsRomanize*Key),
// which the ported lyrics body uses, so these two only drive the vendored AI-romanization toggle.
val AiRomanizeLyricsKey = booleanPreferencesKey("ported_aiRomanizeLyrics")
val AutoAiRomanizeLyricsKey = booleanPreferencesKey("ported_autoAiRomanizeLyrics")
val AutoTranslateLyricsKey = com.mochyy.mochymusic.constants.TranslateLyricsKey
// sora has no excluded-languages list for auto-translate
val AutoTranslateExcludedLanguagesKey = stringSetPreferencesKey("ported_autoTranslateExcludedLanguages")
val TranslatorTargetLangKey = com.mochyy.mochymusic.constants.TranslateLanguageKey
// Neiro's tap haptics (default on). sora's only haptics key, MusicHapticsEnabledKey, is the off-by-default
// vibrate-to-the-beat feature, not tap feedback, so aliasing it would silence the ported buttons' haptics.
val EnableHapticFeedbackKey = booleanPreferencesKey("ported_enableHapticFeedback")
val ArtistSeparatorsKey = stringPreferencesKey("ported_artistSeparators")
val QueueEditLockKey = booleanPreferencesKey("ported_queueEditLock")

val FlamingoBackgroundEffectKey = booleanPreferencesKey("ported_flamingoBackgroundEffect")
val FlamingoHideSlideBarKey = booleanPreferencesKey("ported_flamingoHideSlideBar")
val FlamingoShowVolumeBarKey = booleanPreferencesKey("ported_flamingoShowVolumeBar")
val TikTokMainLyricsEnabledKey = booleanPreferencesKey("ported_tiktokMainLyricsEnabled")

val ProceduralArtworkEnabledKey = booleanPreferencesKey("ported_proceduralArtworkEnabled")
val ProceduralArtworkStyleKey = stringPreferencesKey("ported_proceduralArtworkStyle")
val CanvasAudioReactiveKey = booleanPreferencesKey("ported_canvasAudioReactive")

val VideoAspectRatioKey = stringPreferencesKey("ported_videoAspectRatio")
val VideoPlaybackSpeedKey = floatPreferencesKey("ported_videoPlaybackSpeed")
val VideoAmbientModeKey = booleanPreferencesKey("ported_videoAmbientMode")

enum class ProceduralCanvasStyle {
    KAWARP,
    SQUARE_TUNNEL,
    ;

    companion object {
        fun fromPreference(value: String?): ProceduralCanvasStyle {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: KAWARP
        }
    }
}

enum class SliderStyle {
    Standard,
    Wavy,
    Thick,
    Circular,
    Simple,
}
