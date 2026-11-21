/*
 * The Listen Together port's settings door, in the shape of Neiro's AppSettings
 * facade: the ported engine and screens read these MutableStateFlows and write
 * through the set* functions, exactly as they did against Neiro's AppSettings.
 *
 * Only the Listen Together surface is here (plus autoplay, which the room
 * machinery flips while a guest is in a room), kept in the port's own
 * SharedPreferences file so nothing collides with sora's DataStore keys.
 * Sora's own autoplay/queue behaviour is gated on the room directly in
 * MusicService (the guest never runs the queue runway), not through this
 * value.
 */

package com.mochyy.mochymusic.ported.together.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

object AppSettings {

    /** Whether the queue keeps playing past its end with radio picks. */
    val autoplay = MutableStateFlow(true)

    /** The name this device shows every other member of a Listen Together room. */
    val listenTogetherUsername = MutableStateFlow("")

    /** The WebSocket URL of the Listen Together server a room lives on. */
    val listenTogetherServerUrl = MutableStateFlow("")

    /** The avatar this device joins rooms with — an index asset, or the custom picture. */
    val listenTogetherAvatarIndex = MutableStateFlow(0)

    /** Whether a host lets join requests in without being asked. */
    val listenTogetherAutoApproval = MutableStateFlow(false)

    /** Whether a host takes song suggestions the moment they arrive. */
    val listenTogetherSuggestionAutoApprove = MutableStateFlow(true)

    /** Whether the room's chat pops notifications while the chat screen is closed. */
    val listenTogetherChatNotifications = MutableStateFlow(true)

    /** Whether room events also surface in-app (toasts for suggestions and rooms). */
    val listenTogetherInAppNotifications = MutableStateFlow(true)

    /** Whether a guest's loudness follows the host's device volume. */
    val listenTogetherSyncHostVolume = MutableStateFlow(false)

    /** Whether a guest re-syncs to the host's playhead after a reconnect. */
    val listenTogetherSmartResync = MutableStateFlow(true)

    /** Usernames whose join requests and suggestions never surface again. */
    val listenTogetherBlockedUsers = MutableStateFlow<Set<String>>(emptySet())

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("ported_together_settings", Context.MODE_PRIVATE)
        autoplay.value = prefs.getBoolean("autoplay", true)
        listenTogetherUsername.value = prefs.getString("listenTogetherUsername", null).orEmpty()
        listenTogetherServerUrl.value = prefs.getString("listenTogetherServerUrl", null)?.trim().orEmpty()
        listenTogetherAvatarIndex.value = prefs.getInt("listenTogetherAvatarIndex", 0)
        listenTogetherAutoApproval.value = prefs.getBoolean("listenTogetherAutoApproval", false)
        listenTogetherSuggestionAutoApprove.value = prefs.getBoolean("listenTogetherSuggestionAutoApprove", true)
        listenTogetherChatNotifications.value = prefs.getBoolean("listenTogetherChatNotifications", true)
        listenTogetherInAppNotifications.value = prefs.getBoolean("listenTogetherInAppNotifications", true)
        listenTogetherSyncHostVolume.value = prefs.getBoolean("listenTogetherSyncHostVolume", false)
        listenTogetherSmartResync.value = prefs.getBoolean("listenTogetherSmartResync", true)
        listenTogetherBlockedUsers.value = prefs.getStringSet("listenTogetherBlockedUsers", null) ?: emptySet()
    }

    /** The chat's scroll-back, as the JSON the chat screen keeps it in. Null when empty. */
    fun listenTogetherChatHistory(): String? =
        if (::prefs.isInitialized) prefs.getString("listenTogetherChatHistory", null) else null

    fun setAutoplay(value: Boolean) {
        autoplay.value = value
        if (::prefs.isInitialized) prefs.edit().putBoolean("autoplay", value).apply()
    }

    fun setListenTogetherUsername(value: String) {
        listenTogetherUsername.value = value
        if (::prefs.isInitialized) prefs.edit().putString("listenTogetherUsername", value).apply()
    }

    fun setListenTogetherServerUrl(value: String) {
        listenTogetherServerUrl.value = value.trim()
        if (::prefs.isInitialized) prefs.edit().putString("listenTogetherServerUrl", value).apply()
    }

    fun setListenTogetherAvatarIndex(value: Int) {
        listenTogetherAvatarIndex.value = value
        if (::prefs.isInitialized) prefs.edit().putInt("listenTogetherAvatarIndex", value).apply()
    }

    fun setListenTogetherAutoApproval(value: Boolean) {
        listenTogetherAutoApproval.value = value
        if (::prefs.isInitialized) prefs.edit().putBoolean("listenTogetherAutoApproval", value).apply()
    }

    fun setListenTogetherSuggestionAutoApprove(value: Boolean) {
        listenTogetherSuggestionAutoApprove.value = value
        if (::prefs.isInitialized) prefs.edit().putBoolean("listenTogetherSuggestionAutoApprove", value).apply()
    }

    fun setListenTogetherChatNotifications(value: Boolean) {
        listenTogetherChatNotifications.value = value
        if (::prefs.isInitialized) prefs.edit().putBoolean("listenTogetherChatNotifications", value).apply()
    }

    fun setListenTogetherInAppNotifications(value: Boolean) {
        listenTogetherInAppNotifications.value = value
        if (::prefs.isInitialized) prefs.edit().putBoolean("listenTogetherInAppNotifications", value).apply()
    }

    fun setListenTogetherSyncHostVolume(value: Boolean) {
        listenTogetherSyncHostVolume.value = value
        if (::prefs.isInitialized) prefs.edit().putBoolean("listenTogetherSyncHostVolume", value).apply()
    }

    fun setListenTogetherSmartResync(value: Boolean) {
        listenTogetherSmartResync.value = value
        if (::prefs.isInitialized) prefs.edit().putBoolean("listenTogetherSmartResync", value).apply()
    }

    fun setListenTogetherBlockedUsers(value: Set<String>) {
        listenTogetherBlockedUsers.value = value
        if (::prefs.isInitialized) prefs.edit().putStringSet("listenTogetherBlockedUsers", value).apply()
    }

    fun setListenTogetherChatHistory(value: String?) {
        if (!::prefs.isInitialized) return
        if (value == null) {
            prefs.edit().remove("listenTogetherChatHistory").apply()
        } else {
            prefs.edit().putString("listenTogetherChatHistory", value).apply()
        }
    }
}
