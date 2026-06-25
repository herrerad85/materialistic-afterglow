/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

/**
 * The poll seam over the Hacker News data layer (ADR-0004 / E5-D7 G2). Returns only plain types
 * ([PolledParent] / [CandidateChild]) so the reply poller stays testable with a fake source and
 * never sees Retrofit, Rx, or the HN item model. The real implementation
 * ([HackerNewsReplyDataSource]) adapts `UserManager` + `ItemManager`; unit tests bind a fake.
 */
interface ReplyDataSource {

  /**
   * The signed-in user's most-recent [limit] submitted items, each carrying the ids of its current
   * direct children (read straight from the parent payload, so no child fetch is needed to seed or
   * diff). A genuinely empty submitted history returns an empty list; a fetch failure throws (the
   * worker maps that to a retry, never to "the user has no items").
   */
  suspend fun recentSubmittedParents(username: String, limit: Int): List<PolledParent>

  /**
   * Fetch each candidate child payload so its author / dead / deleted state can be judged. Only the
   * newly observed kids reach here (E5-D2), so the fan-out is bounded by the per-poll diff.
   */
  suspend fun children(kidIds: List<String>): List<CandidateChild>
}
