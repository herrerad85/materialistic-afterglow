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

/**
 * The per-username seeded marker / last-poll timestamp ([ReplyPollState]). See ADR-0004 / E5-D5.
 */
@Dao
interface ReplyPollStateDao {

  /**
   * True once this user has had a successful poll, the silent-first-seed gate (never use row
   * count).
   */
  @Query("SELECT EXISTS(SELECT 1 FROM reply_poll_state WHERE username = :username)")
  fun isSeeded(username: String): Boolean

  @Query("SELECT * FROM reply_poll_state WHERE username = :username LIMIT 1")
  fun get(username: String): ReplyPollState?

  @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsert(state: ReplyPollState)

  @Query("DELETE FROM reply_poll_state WHERE username = :username")
  fun deleteForUser(username: String)
}
