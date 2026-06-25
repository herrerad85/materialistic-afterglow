/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The "new comment" baseline (G6): one row per story holding the highest comment id seen on the
 * last visit to its thread ([maxSeenId]). On re-opening a thread, any loaded comment whose id is
 * strictly greater than this baseline arrived since that visit and is marked new.
 *
 * This relies on Hacker News assigning item ids globally and monotonically by creation time, so a
 * comment posted after your last visit always has a higher id than every comment that existed then.
 * A single max-id baseline is therefore enough; no per-comment seen-set is needed. If HN ever
 * breaks that property, switch to a per-comment seen-set.
 */
@Entity(tableName = "comment_seen")
data class CommentSeen(
    @PrimaryKey @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "max_seen_id") val maxSeenId: Long,
)
