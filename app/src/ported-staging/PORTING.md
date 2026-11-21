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
