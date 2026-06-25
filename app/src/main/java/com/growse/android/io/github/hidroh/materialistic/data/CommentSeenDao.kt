/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Per-story access to the "new comment" baseline ([CommentSeen]). See G6. */
@Dao
interface CommentSeenDao {

  /**
   * The highest comment id seen on the last visit to [storyId]'s thread, or null on first visit (no
   * row yet). Read BEFORE updating, so the new-comment diff sees the old baseline.
   */
  @Query("SELECT max_seen_id FROM comment_seen WHERE story_id = :storyId")
  fun maxSeen(storyId: String): Long?

  @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertIfMissing(row: CommentSeen)

  @Query(
      "UPDATE comment_seen SET max_seen_id = MAX(max_seen_id, :maxSeenId) WHERE story_id = :storyId"
  )
  fun raiseMaxSeen(storyId: String, maxSeenId: Long)

  /**
   * Monotonically advance [storyId]'s baseline to [maxSeenId]: create the row if missing, then
   * raise the stored max (the SQL MAX never lowers it). One transaction so concurrent advances
   * serialize and a lower advance arriving after a higher one cannot regress the baseline.
   * MAX-of-two and the IGNORE insert use only baseline SQLite, so no modern UPSERT syntax is
   * required (API 23 safe).
   */
  @Transaction
  fun advanceMaxSeen(storyId: String, maxSeenId: Long) {
    insertIfMissing(CommentSeen(storyId, maxSeenId))
    raiseMaxSeen(storyId, maxSeenId)
  }
}
