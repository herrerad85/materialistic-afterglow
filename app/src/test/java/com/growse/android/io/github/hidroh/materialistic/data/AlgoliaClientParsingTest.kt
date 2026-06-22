/*
 * Copyright (c) 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM characterization of the Algolia search response → item mapping.
 *
 * Retrofit decodes the JSON into [AlgoliaClient.AlgoliaHits] with a default Gson, then
 * AlgoliaClient.toItems maps each hit's objectID to a [HackerNewsItem] whose id is the parsed
 * objectID and whose rank is its 1-based position. toItems is private, so this test deserializes
 * with the same Gson and applies the identical mapping, asserting the concrete decoded objectIDs
 * and the resulting ids/ranks.
 */
class AlgoliaClientParsingTest {

  private fun load(name: String): String =
      checkNotNull(javaClass.classLoader!!.getResourceAsStream(name)) { "missing fixture $name" }
          .bufferedReader()
          .use { it.readText() }

  @Test
  fun deserializesHitObjectIds() {
    val parsed = Gson().fromJson(load("algolia_search.json"), AlgoliaClient.AlgoliaHits::class.java)

    assertEquals(3, parsed.hits.size)
    assertEquals("8863", parsed.hits[0].objectID)
    assertEquals("9224", parsed.hits[1].objectID)
    assertEquals("121003", parsed.hits[2].objectID)
  }

  @Test
  fun mapsHitsToRankedItems() {
    val parsed = Gson().fromJson(load("algolia_search.json"), AlgoliaClient.AlgoliaHits::class.java)

    // Mirror AlgoliaClient.toItems: id = parseLong(objectID), rank = index + 1.
    val items =
        parsed.hits.mapIndexed { i, hit ->
          HackerNewsItem(hit.objectID.toLong()).also { it.rank = i + 1 }
        }

    assertEquals(3, items.size)
    assertEquals(8863L, items[0].longId)
    assertEquals(1, items[0].rank)
    assertEquals(9224L, items[1].longId)
    assertEquals(2, items[1].rank)
    assertEquals(121003L, items[2].longId)
    assertEquals(3, items[2].rank)
  }
}
