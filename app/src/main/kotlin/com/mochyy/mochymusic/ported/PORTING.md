# Neiro Apple Music (flamingo) and TikTok styles: staged, not compiled

These 12 files are Neiro's `archivetuneplayer` sources copied verbatim: FlamingoPlayer, FlamingoBackground,
FlamingoSupport, AppleMusicQueueSheet, AppleMusicSleepTimerSheet and the seven TikTok files. Only the package
lines changed and every `moe.rukamori.archivetune.*` import was pointed at `com.mochyy.mochymusic.ported.bridge`.
`src/ported-staging` is not a Gradle source set, so nothing here is built until it moves to `src/main/kotlin`.

Before it can move, `ported/bridge` has to supply the 83 symbols these files import:

- Re-export or alias sora's own: LocalPlayerConnection, LocalDatabase, LocalSyncUtils, MediaMetadata, FormatEntity,
  ArtistEntity, Artist, AddToPlaylistDialog, ShowMediaInfo, SleepTimer, CanvasArtworkPlayer, YouTube, rememberPreference.
- Keys with Neiro's names and defaults: Flamingo*, TikTokMainLyricsEnabledKey, QueueEditLockKey, ProceduralArtworkEnabledKey.
  sora already has AiRomanizeLyricsKey, AutoTranslate*, TranslatorTargetLangKey and EnableHapticFeedbackKey.
- Copy verbatim from Neiro: BottomSheetMenu/BottomSheetPage (MenuState, BottomSheetPageState and their hosts),
  ThumbnailSwapState, ProceduralCanvas, VideoQualitySheet, the InlineVideoPlayer pieces, BitChordWander and LyricsUtils
  (hasTranslation, shouldAutoTranslate, isTtml, isLineSyncedLrc).
- Thin shims: LyricsEntity.Source / `.source` (from sora's `provider`), MediaMetadata.isMusicVideo (from isVideoSong),
  codecLabel/isLossless on FormatEntity, LyricsMenuViewModel (translateLyricsWithAi goes to
  LyricsTranslationHelper.triggerManualTranslation), and a NavController like Neiro's BridgeNavController.
- Shared with sora, as asked: driftArtworkSlot goes to ui/player/ArtworkDrift.kt. LyricsEnhanced goes to sora's
  lyrics renderer (LyricsScreen / ExpressiveLyrics).
- Still to copy: every R.drawable, R.string and R.font these files use, with a `ported_` prefix wherever sora already
  has the same name.

## Status (resumed round)
- Done and compiled with the app (src/main): all flamingo/TikTok drawables (66 new + 12 as `ported_*`), the
  Neiro-only video/settings drawables, strings in res/values/strings_ported.xml (`ported_*` on clashes),
  SF Pro Display fonts and Neiro's type scale (ported/theme/PortedTheme.kt, `PortedTypography`).
- Staged here, verbatim, not compiled yet: the 12 player files plus Neiro-only helpers in bridge/vendored
  (ThumbnailSwapState, ProceduralCanvas, VideoQualitySheet, InlineVideoPlayer, BitChordWander,
  BottomSheetMenu/Page, YouTubeUtils, StatusBarDialog, ShapeUtils, OfflineArtworkImageRequest,
  LocalMediaIntents, StringUtils, ImageBlurUtils).
- Bridge mapping decided (to write next): PlatformBackdrop = kyant LayerBackdrop; rememberBackdrop(color) =
  rememberLayerBackdrop { drawRect(color); drawContent() }; LiquidGlassTuning = ArchiveTune's class copied
  verbatim, filled from sora's LocalLiquidGlass; LyricsEnhanced -> sora ExpressiveLyrics/LyricsScreen;
  AnchoredLyricsOverflowMenu -> sora LyricsToolSheets; PlayerMenu -> sora PlayerActionMenu; SleepTimer,
  AddToPlaylistDialog, CanvasArtworkPlayer, MediaMetadataListItem, LocalDatabase, LocalPlayerConnection ->
  sora's own; YouTube.getLikeCount -> null (count hidden) unless innertube exposes likeCount;
  rememberCastPlayerMenuAction -> null; AutoMixUiState.mixing -> MutableStateFlow(false);
  Neiro-only keys stored with a "ported_" prefix.

## Bridge status (commit after 9169f27)

Written in ported/bridge (still staged, not compiled): Bridge.kt (typealiases to sora's PlayerConnection,
MediaMetadata, FormatEntity, ArtistEntity, Artist, SleepTimer, BottomSheetState, PopupSheetState, LiquidGlassTuning;
locals; PreferenceStore; AutoMixUiState; YouTube.artist and getLikeCount -> null so the count hides; LyricsEntity.Source;
metadata/move/isMusicVideo/localPlayer/crossfadeMixing/togglePlayPause/toggleRepeatMode; Neiro's codecLabel/isLossless;
LyricsUtils; LyricsMenuViewModel -> LyricsTranslationHelper.triggerManualTranslation; VideoAspectRatio) and
BridgeKeys.kt (sora's lyrics/haptic keys re-exported, the Neiro-only keys stored as "ported_<name>" with Neiro's defaults).

Still missing (from the static symbol check):
- Composables to wrap sora's: AddToPlaylistDialog, AnchoredLyricsOverflowMenu (-> LyricsToolSheets), PlayerMenu
  (-> PlayerActionMenu), ShowMediaInfo, MediaMetadataListItem, CanvasArtworkPlayer + CanvasLoopSync, LyricsEnhanced
  (-> sora's ExpressiveLyrics with its animation setting), driftArtworkSlot (-> ArtworkDrift).
- Glass: PlatformBackdrop, layerBackdrop, rememberBackdrop, rememberLiquidGlassEnabled.
- Prefs/state: rememberPreference, rememberEnumPreference, rememberLowDataModeActive (-> DataSaver),
  rememberCastPlayerMenuAction (returns null; sora has no cast).
- Neiro files still to copy verbatim: VideoArtworkState/VideoArtworkSurface (VideoArtworkPlayer.kt; its playback should
  sit on sora's YouTubeVideoSession), KenBurnsCanvas, CanvasAudioReactor (rememberBassEnergy), VideoQualityPreference.
- Then: move flamingo/tiktok/bridge into src/main, PortedPlayerHost (Neiro Player.kt ~1500-1720), rewire
  AlternatePlayerStyles APPLE_MUSIC/TIKTOK, delete ui/player/applemusic and ui/player/tiktok, push and loop on CI.

Review round fixes (4ba8420): AutoTranslateLyricsKey -> sora TranslateLyricsKey, TranslatorTargetLangKey -> sora
TranslateLanguageKey. Kept as ported_: EnableHapticFeedbackKey (sora's MusicHapticsEnabledKey is the off-by-default
vibrate-to-the-beat feature, not tap feedback), AiRomanize/AutoAiRomanize (sora has no AI romanization; its per-language
romanization applies through sora's lyrics renderer), AutoTranslateExcludedLanguagesKey (no sora equivalent).
TikTok like count stays hidden: innertube's likeCountNotliked (YouTube.kt:1668/1760) is the like count of a *comment*,
not of the video, so sora has no video like count to show. LyricsUtils.hasTranslation stays false until it reads
sora's LyricsEntity.translatedLyrics in the move-in round.

Status (round 9, commit after e441b705's restore):
- Bridge symbols: all names the staged files import now have a definition (BridgeUi.kt, BridgeVideo.kt),
  plus Neiro's PredictiveBack, KenBurnsCanvas, CanvasAudioReactor, VideoQualityPreference, PlayerSlider,
  PlayerSliderColors and ActionPromptDialog copied verbatim into bridge/vendored.
- Menus: PlayerMenu / AnchoredLyricsOverflowMenu ask the host (LocalPortedMenus) to open sora's PlayerActionMenu;
  the host must call PortedMenuLayer(menus) over the style.
- Video artwork: BridgeVideo.kt adapts sora's YouTubeVideoSession behind Neiro's VideoArtworkState names.
- Lyrics: LyricsEnhanced renders sora's LyricsScreen (header off), or ExpressiveLyrics for TikTok's single line.
Closed in round 10 (ab6bbf8f and the commit after it):
- SquigglySlider: Neiro's own library, `me.saket.squigglyslider:squigglyslider:1.0.0` (gradle/libs.versions.toml,
  app/build.gradle.kts), the artifact Neiro's archivetuneplayer/build.gradle.kts:186 uses.
- trackRange: material3 1.5.0-alpha18 names it valueRange; the two vendored call sites say so (`// BRIDGE(neiro)`).
- requestRecord: ArchiveTune's ThrottledLayerBackdrop, PlatformBackdrop, rememberBackdrop and layerBackdrop copied
  verbatim into bridge/vendored/ThrottledLayerBackdrop.kt (the kyant LayerBackdrop alias is gone).
- upsert: `DatabaseDao.upsert(ArtistEntity)` in Bridge.kt (sora's insert ignores conflicts, so it updates after).
- LyricsUtils.hasTranslation: Neiro's implementation, verbatim, in Bridge.kt.
- False positives of the stubbed check, nothing to do: FormatEntity.sampleRate, Timeline.Window.uid and the
  LiquidGlassTuning factors (tintFactor, blurFactor, shadowFactor, saturation) all exist in sora.
- Host helpers: bridge/BridgeHost.kt has a NavController the styles navigate on (artist, album, search, home,
  library go to sora's style actions, as Neiro's BridgeNavController does) and the song's canvas lookup.

Left (not started; the build still uses sora's old applemusic/tiktok styles):
- Write ported/PortedPlayerHost.kt from Neiro Player.kt ~1540-1700: PortedTypography theme, LocalMenuState /
  LocalBottomSheetPageState / LocalVideoArtworkState / LocalLiquidGlassTuning, sliderPosition + seek as Neiro's
  onSliderValueChange(Finished), BottomSheetMenu, BottomSheetPage and PortedMenuLayer over the style, a still
  frame when preview is true.
- Move flamingo/tiktok/bridge into src/main, route APPLE_MUSIC/TIKTOK to the host in AlternatePlayerStyles.kt,
  delete ui/player/applemusic and ui/player/tiktok, push, fix CI.
