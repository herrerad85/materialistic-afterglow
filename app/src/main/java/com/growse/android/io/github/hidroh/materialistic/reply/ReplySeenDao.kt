/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Username-scoped access to the reply-detection baseline ([ReplySeen]). See ADR-0004. */
@Dao
interface ReplySeenDao {

  /**
   * Every kid id already observed for this user. The detector diffs current kids against this set.
   */
  @Query("SELECT kid_id FROM reply_seen WHERE username = :username")
  fun seenKidIds(username: String): List<String>

  @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertAll(rows: List<ReplySeen>)

  /**
   * Prune-by-window (E5-D5): drop this user's baseline rows whose parent has aged out of the
   * recent-item window. A parent that leaves the window is never polled again, so forgetting its
   * kids can't re-notify. Callers must pass a non-empty window; an empty `parentIds` would delete
   * every row for the user.
   */
  @Query("DELETE FROM reply_seen WHERE username = :username AND parent_id NOT IN (:parentIds)")
  fun pruneOutsideWindow(username: String, parentIds: List<String>)

  @Query("DELETE FROM reply_seen WHERE username = :username") fun deleteForUser(username: String)
}
