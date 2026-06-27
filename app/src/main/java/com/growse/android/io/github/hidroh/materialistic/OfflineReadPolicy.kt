/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import android.content.Context
import android.net.ConnectivityManager
import com.growse.android.io.github.hidroh.materialistic.data.ItemManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Why a cache-only-capable read came back empty (#25). Explicit Offline mode and a real
 * no-connection state are distinct user situations: only the former is cleared by the "turn off
 * offline mode" off-ramp, so each surface (list / comments / article) shows distinct copy. When the
 * device is online, an empty result is an ordinary fetch error, not an offline state.
 */
enum class OfflineEmptyReason {
  OFFLINE_MODE,
  NO_CONNECTION,
  ONLINE_ERROR,
}

/**
 * The offline-read policy: whether a read should stay on cached content (#22), the cache mode that
 * decision maps to, and which empty-state a cache-only read is in (#25).
 *
 * This is the injectable seam. Hilt-managed callers (the story-list ViewModel, the list fragment)
 * depend on this interface so a test can substitute a fake; [DefaultOfflineReadPolicy] binds the
 * live device state. The behavior itself is the single source of truth in [OfflineRead], so plain
 * View / Java call sites that cannot inject still reach the same logic through that object (for now
 * via temporary AppUtils wrappers). The shared low-level HTTP interceptor deliberately does NOT
 * consult this; it stays connectivity-only (see NetworkModule).
 */
interface OfflineReadPolicy {
  /**
   * True when reading should stay cached: explicit Offline mode is on, or there is no connection.
   */
  fun shouldReadCacheOnly(): Boolean

  /**
   * Maps a requested [ItemManager] cache mode to the one to actually use: strict
   * [ItemManager.MODE_CACHE_ONLY] when [shouldReadCacheOnly], else the requested mode unchanged (so
   * an online MODE_NETWORK swipe-refresh is still honored).
   */
  fun effectiveCacheMode(requested: Int): Int

  /** Which distinct empty-state this surface is in right now. */
  fun emptyReason(): OfflineEmptyReason
}

/** Binds the live device state ([Context]) into the [OfflineReadPolicy] seam. */
class DefaultOfflineReadPolicy
@Inject
constructor(@ApplicationContext private val context: Context) : OfflineReadPolicy {
  override fun shouldReadCacheOnly(): Boolean = OfflineRead.shouldReadCacheOnly(context)

  override fun effectiveCacheMode(requested: Int): Int =
      OfflineRead.effectiveCacheMode(context, requested)

  override fun emptyReason(): OfflineEmptyReason = OfflineRead.emptyReason(context)
}

/**
 * Non-Hilt single source of truth for the offline-read decision, callable with a [Context] from any
 * site (the [OfflineReadPolicy] adapter, plain Views, and the temporary AppUtils wrappers) without
 * Hilt or a service locator. Reading-only: nothing here triggers a download.
 */
object OfflineRead {
  /**
   * Live connectivity, independent of explicit Offline mode (NetworkModule relies on that split).
   */
  @JvmStatic
  @Suppress("DEPRECATION")
  fun hasConnection(context: Context): Boolean {
    val activeNetworkInfo =
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
            .activeNetworkInfo
    return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting
  }

  /** #22: stay on cached content when explicit Offline mode is on OR there is no connectivity. */
  @JvmStatic
  fun shouldReadCacheOnly(context: Context): Boolean =
      Preferences.isOfflineMode(context) || !hasConnection(context)

  /** #22: strict cache-only when [shouldReadCacheOnly], else the requested mode unchanged. */
  @JvmStatic
  fun effectiveCacheMode(context: Context, requested: Int): Int =
      if (shouldReadCacheOnly(context)) ItemManager.MODE_CACHE_ONLY else requested

  /** #25: the empty-state reason from the live device state. */
  @JvmStatic
  fun emptyReason(context: Context): OfflineEmptyReason =
      emptyReason(Preferences.isOfflineMode(context), hasConnection(context))

  /** Pure mapping (no Android dependency) so the copy-selection logic is unit-testable. */
  fun emptyReason(offlineModeOn: Boolean, hasConnection: Boolean): OfflineEmptyReason =
      when {
        offlineModeOn -> OfflineEmptyReason.OFFLINE_MODE
        hasConnection -> OfflineEmptyReason.ONLINE_ERROR
        else -> OfflineEmptyReason.NO_CONNECTION
      }
}
