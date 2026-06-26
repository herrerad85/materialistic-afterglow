/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

/**
 * Per-item offline availability (#23). Fully [CACHED] means the item data plus comments plus the
 * selected reading surface (article archive or reader text) are present; [PARTIALLY_CACHED] means
 * some but not all of those; [NOT_CACHED] means no on-device copy.
 */
enum class OfflineStatus {
  CACHED,
  PARTIALLY_CACHED,
  NOT_CACHED,
}
