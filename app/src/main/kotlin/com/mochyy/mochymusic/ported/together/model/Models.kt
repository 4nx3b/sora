package com.mochyy.mochymusic.ported.together.model

/** Tier of an item in the playback queue. */
enum class QueueTier {
    /** Explicitly queued by user ("Play Next", "Add to Queue", or preserved user queue). */
    USER_QUEUE,
    /** Part of the active Album, Playlist, or Artist collection context. */
    CONTEXT,
    /** Dynamically generated radio recommendations appended when context/user queue ends. */
    AUTOPLAY,
}

/**
 * One credited artist, as YouTube Music itself states them.
 *
 * @property browseId The artist's own channel, so their page is the one that
 * opens. Null for the rare run that names an artist without linking one.
 */
data class ArtistRef(
    val name: String,
    val browseId: String? = null,
)

/** A playable YouTube Music track. */
data class Song(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationText: String? = null,
    /** Browse ids lifted from the row, used by the long-press actions. */
    val artistId: String? = null,
    /**
     * Every credited artist, each with their own channel, where the row said so.
     *
     * [artistId] is the lead's and nothing else, which is why a collaboration
     * could only ever be opened at its first name. A track's byline carries one
     * navigation endpoint per artist, so the rest of them are here rather than
     * left in the response — and because a name is not an identity, this is the
     * only thing that can open the right page for the second and third credit.
     *
     * Empty on rows that state no per-artist links, which is most home cards
     * and anything read before its watch-queue entry has been looked up.
     */
    val artists: List<ArtistRef> = emptyList(),
    val albumId: String? = null,
    /** Names the album page header, which [albumId] alone can't. */
    val albumName: String? = null,
    /** A music-video upload rather than the catalogue track. */
    val isVideo: Boolean = false,
    /**
     * True for a video upload and for its manually selected catalogue match.
     * The latter remains video-origin so playback policies such as AutoMix do
     * not mistake a converted video for a normal music track.
     */
    val isVideoOrigin: Boolean = isVideo,
    /**
     * This track's identity *within one playlist*, which is not its [videoId]:
     * the same song added twice is two entries with two set-video-ids, and
     * removing one of them is only expressible in those terms. Present only on
     * rows parsed from a playlist page, which is the only place a removal can
     * be asked for from.
     */
    val setVideoId: String? = null,
    /** Which queue tier this track belongs to in the playback timeline. */
    val queueTier: QueueTier = QueueTier.CONTEXT,
    /**
     * Unique, strictly immutable queue-entry identity assigned when this track
     * enters the player's queue in QueueCoordinator. Preserved across all stream transformations
     * and persistence.
     */
    val queueEntryId: String? = null,
    /**
     * The seed title of an explicitly started radio queue. Every item in that
     * queue carries the same value, so the player can keep naming the station
     * across skips and queue edits without holding UI-only session state.
     */
    val radioName: String? = null,
    /**
     * Explicit content or file URI for local device tracks or downloaded audio.
     */
    val localUri: String? = null,
    /** A premium rendition marker recorded by BitChord for its Downloads list. */
    val downloadFormat: String? = null,
    /**
     * Real filesystem path backing [localUri], when MediaStore exposes one.
     * Lets playback swap a content:// row for a raw file:// path on formats
     * that need it — see [com.music.bitchord.playback.toMediaItem].
     */
    val localPath: String? = null,
    /** MediaStore timestamps used only to sort device and downloaded libraries. */
    val localDateAddedSeconds: Long? = null,
    val localDateModifiedSeconds: Long? = null,
    /**
     * Track number within its album, from the file's own tags (`TRACK` in
     * MediaStore, `CD_TRACK_NUMBER` via retriever). Null when the tags don't
     * say — remote catalogue rows never carry one.
     *
     * Lets a local album list in release order instead of whatever the library
     * sort left standing, and lets its rows show the release's own numbers
     * rather than their position in that list.
     */
    val trackNumber: Int? = null,
    /**
     * What a non-YouTube source says it can serve this recording at, as one of
     * `LOSSLESS`, `HIGH` or `LOW` — null for every row that didn't come from
     * one.
     *
     * Carried on the row rather than discovered at stream time because it is
     * the only thing that distinguishes two catalogues holding the same track,
     * and the choice between them has to be made *before* either is asked for
     * a URL. Without it the picker was blind: a Deezer row and a 16-bit FLAC
     * row looked identical, the FLAC lost a tie-break on artist spelling, and
     * the track played as a 128kbps MP3.
     */
    val sourceQuality: String? = null,
    /**
     * Whether a `LOSSLESS` [sourceQuality] row said it was the hi-res tier
     * (`HI_RES_LOSSLESS`, `hires-96`, `24-bit`). Display only — the resolver
     * ranks on [sourceQuality], where 16-bit and 24-bit are one tier.
     */
    val sourceHiRes: Boolean = false,
    /** Explicit-content state from the catalogue; null when that source does not say. */
    val isExplicit: Boolean? = null,
    /**
     * The page, collection, or feed section that put this track in the queue.
     *
     * This is deliberately separate from [albumName]: a song can belong to an
     * album while it was actually played from Search, History, a playlist, or
     * a recommendation shelf. Kept on every queue item so Now Playing can name
     * that origin after skips and after the playback service restores a queue.
     */
    val playbackSource: String? = null,
    /** Where tapping [playbackSource] should return in the app. */
    val playbackSourceType: PlaybackSourceType? = null,
    /** Browse id for an album, playlist, or other source page. */
    val playbackSourceId: String? = null,
    /**
     * Feedback token extracted from the watch-queue row's menu — used to remove
     * this entry from the account's listening history on YouTube Music. Null on
     * non-history rows (e.g. search results, library).
     */
    val feedbackToken: String? = null,
) {
    /** Legacy derived property: true if and only if [queueTier] is [QueueTier.AUTOPLAY]. */
    val fromAutoplay: Boolean get() = queueTier == QueueTier.AUTOPLAY

    /** Backward-compatibility constructor for callers passing legacy [fromAutoplay]. */
    constructor(
        videoId: String,
        title: String,
        artist: String,
        thumbnailUrl: String?,
        durationText: String? = null,
        artistId: String? = null,
        artists: List<ArtistRef> = emptyList(),
        albumId: String? = null,
        albumName: String? = null,
        isVideo: Boolean = false,
        isVideoOrigin: Boolean = isVideo,
        setVideoId: String? = null,
        fromAutoplay: Boolean,
        radioName: String? = null,
        localUri: String? = null,
        downloadFormat: String? = null,
        localPath: String? = null,
        localDateAddedSeconds: Long? = null,
        localDateModifiedSeconds: Long? = null,
        trackNumber: Int? = null,
        sourceQuality: String? = null,
        isExplicit: Boolean? = null,
        playbackSource: String? = null,
        playbackSourceType: PlaybackSourceType? = null,
        playbackSourceId: String? = null,
    ) : this(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationText = durationText,
        artistId = artistId,
        artists = artists,
        albumId = albumId,
        albumName = albumName,
        isVideo = isVideo,
        isVideoOrigin = isVideoOrigin,
        setVideoId = setVideoId,
        queueTier = if (fromAutoplay) QueueTier.AUTOPLAY else QueueTier.CONTEXT,
        queueEntryId = null,
        radioName = radioName,
        localUri = localUri,
        downloadFormat = downloadFormat,
        localPath = localPath,
        localDateAddedSeconds = localDateAddedSeconds,
        localDateModifiedSeconds = localDateModifiedSeconds,
        trackNumber = trackNumber,
        sourceQuality = sourceQuality,
        isExplicit = isExplicit,
        playbackSource = playbackSource,
        playbackSourceType = playbackSourceType,
        playbackSourceId = playbackSourceId,
    )
}

enum class PlaybackSourceType {
    HOME,
    SEARCH,
    HISTORY,
    REPLAY,
    EXPLORE,
    BROWSE,
    SHARED_LINK,
    QUEUE,
}
