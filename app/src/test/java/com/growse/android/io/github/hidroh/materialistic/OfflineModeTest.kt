/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import android.content.Context
import android.net.ConnectivityManager
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.growse.android.io.github.hidroh.materialistic.data.HackerNewsClient
import com.growse.android.io.github.hidroh.materialistic.data.ItemManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Characterizes the #22 explicit offline-mode preference, the single effective "read cache only"
 * decision in [AppUtils.shouldReadCacheOnly], the call-site cache-mode mapping in
 * [AppUtils.effectiveCacheMode], and the guarantee that explicit offline mode does NOT change the
 * low-level [NetworkModule.ConnectionAwareInterceptor] (which stays connectivity-only). Robolectric
 * is required for the SharedPreferences and ConnectivityManager.
 */
@RunWith(RobolectricTestRunner::class)
class OfflineModeTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    Preferences.reset(context)
  }

  @Test
  fun offlineMode_defaultsOffAndRoundTrips() {
    assertFalse(Preferences.isOfflineMode(context))

    Preferences.setOfflineMode(context, true)
    assertTrue(Preferences.isOfflineMode(context))

    Preferences.setOfflineMode(context, false)
    assertFalse(Preferences.isOfflineMode(context))
  }

  @Test
  fun offlineMode_isSeparateFromSaveForOfflinePopulation() {
    // Turning explicit offline mode on must not enable the save-for-offline population preference.
    Preferences.setOfflineMode(context, true)
    assertTrue(Preferences.isOfflineMode(context))
    assertFalse(Preferences.Offline.isEnabled(context))

    // ...and enabling save-for-offline must not flip explicit offline mode on.
    Preferences.reset(context)
    PreferenceManager.getDefaultSharedPreferences(context)
        .edit()
        .putBoolean(context.getString(R.string.pref_saved_item_sync), true)
        .commit()
    assertTrue(Preferences.Offline.isEnabled(context))
    assertFalse(Preferences.isOfflineMode(context))
  }

  @Test
  fun shouldReadCacheOnly_isForcedWhenOfflineModeOn() {
    // Offline mode on forces cache-only reading regardless of connectivity.
    Preferences.setOfflineMode(context, true)
    assertTrue(AppUtils.shouldReadCacheOnly(context))
  }

  @Test
  fun shouldReadCacheOnly_followsConnectivityWhenOfflineModeOff() {
    // Offline mode off falls back to live connectivity (existing behavior), with no extra forcing.
    Preferences.setOfflineMode(context, false)
    assertEquals(!AppUtils.hasConnection(context), AppUtils.shouldReadCacheOnly(context))
  }

  @Test
  fun effectiveCacheMode_mapsToStrictCacheOnlyWhenOfflineModeOn() {
    // Reader/list/item reads become STRICT cache-only (MODE_CACHE_ONLY, not the graceful-fallback
    // MODE_CACHE) when offline mode is on, even for an explicit MODE_NETWORK request (e.g.
    // swipe-refresh).
    Preferences.setOfflineMode(context, true)
    assertEquals(
        ItemManager.MODE_CACHE_ONLY,
        AppUtils.effectiveCacheMode(context, ItemManager.MODE_DEFAULT),
    )
    assertEquals(
        ItemManager.MODE_CACHE_ONLY,
        AppUtils.effectiveCacheMode(context, ItemManager.MODE_NETWORK),
    )
  }

  @Test
  fun effectiveCacheMode_honorsRequestedModeWhenOnlineAndOfflineModeOff() {
    // With offline mode off and connectivity present (Robolectric default), the requested mode is
    // honored unchanged, so explicit-network callers keep their behavior.
    Preferences.setOfflineMode(context, false)
    assertEquals(
        ItemManager.MODE_DEFAULT,
        AppUtils.effectiveCacheMode(context, ItemManager.MODE_DEFAULT),
    )
    assertEquals(
        ItemManager.MODE_NETWORK,
        AppUtils.effectiveCacheMode(context, ItemManager.MODE_NETWORK),
    )
  }

  @Test
  fun connectionAwareInterceptor_ignoresOfflineMode_whenConnected() {
    // Regression guard for #22: explicit offline mode must NOT make the shared HTTP interceptor
    // force cache. With connectivity present, an offline-mode-on request is forwarded unchanged
    // (no only-if-cached header), so MODE_NETWORK / no-cache callers are not converted at the
    // HTTP layer.
    Preferences.setOfflineMode(context, true)
    val interceptor = NetworkModule.ConnectionAwareInterceptor(context)
    val forwarded = forwardThrough(interceptor, cacheEnabledHostRequest())
    assertNull(forwarded.header("Cache-Control"))
  }

  @Test
  fun connectionAwareInterceptor_forcesCacheOnlyWhenNoConnectivity() {
    // The interceptor's only trigger is a genuine no-connection state, independent of offline mode.
    Preferences.setOfflineMode(context, false)
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    shadowOf(cm).setActiveNetworkInfo(null)
    val interceptor = NetworkModule.ConnectionAwareInterceptor(context)
    val forwarded = forwardThrough(interceptor, cacheEnabledHostRequest())
    assertEquals(CacheControl.FORCE_CACHE.toString(), forwarded.header("Cache-Control"))
  }

  private fun cacheEnabledHostRequest(): Request =
      Request.Builder().url("https://" + HackerNewsClient.HOST + "/v0/item/1.json").build()

  /** Runs [request] through [interceptor] and returns the request it forwarded to the chain. */
  private fun forwardThrough(interceptor: Interceptor, request: Request): Request {
    val chain = mockk<Interceptor.Chain>()
    val forwarded = slot<Request>()
    every { chain.request() } returns request
    every { chain.proceed(capture(forwarded)) } answers
        {
          Response.Builder()
              .request(forwarded.captured)
              .protocol(Protocol.HTTP_1_1)
              .code(200)
              .message("OK")
              .body("".toResponseBody(null))
              .build()
        }
    interceptor.intercept(chain)
    return forwarded.captured
  }
}
