// small least-recently-used maps for the caches singletons keep per song or artist, so they stop growing for the whole run

package com.mochyy.mochymusic.utils

import java.util.Collections

fun <K, V> lruMap(max: Int): MutableMap<K, V> = Collections.synchronizedMap(object : LinkedHashMap<K, V>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > max
})

fun <K> lruSet(max: Int): MutableSet<K> = Collections.newSetFromMap(lruMap<K, Boolean>(max))
