/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the in-app deep-link URI contract that moved out of AppUtils: the scheme is the build's
 * [BuildConfig.APPLICATION_ID] (the one applicationId-derived surface here), and a created
 * item/user URI round-trips back to its id through [ItemUris.getDataUriId]. A web URI falls back to
 * the query parameter. This pins the scheme to BuildConfig so private (combinator) and public
 * (afterglow) builds each keep their own.
 */
@RunWith(RobolectricTestRunner::class)
class ItemUrisTest {

  @Test
  fun itemUri_usesApplicationIdSchemeAndRoundTrips() {
    val uri = ItemUris.createItemUri("42")
    assertEquals(BuildConfig.APPLICATION_ID, uri.scheme)
    assertEquals("item", uri.authority)
    assertEquals("42", ItemUris.getDataUriId(Intent().setData(uri), "id"))
  }

  @Test
  fun userUri_usesApplicationIdSchemeAndRoundTrips() {
    val uri = ItemUris.createUserUri("pg")
    assertEquals(BuildConfig.APPLICATION_ID, uri.scheme)
    assertEquals("user", uri.authority)
    assertEquals("pg", ItemUris.getDataUriId(Intent().setData(uri), "id"))
  }

  @Test
  fun webUri_fallsBackToQueryParameter() {
    val intent = Intent().setData(Uri.parse("https://example.com/x?id=99"))
    assertEquals("99", ItemUris.getDataUriId(intent, "id"))
  }
}
