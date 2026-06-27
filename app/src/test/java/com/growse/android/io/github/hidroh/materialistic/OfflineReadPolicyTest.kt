/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM characterization of the offline-read empty-reason mapping (#25), extracted from AppUtils
 * into [OfflineRead]. Explicit Offline mode, a real no-connection state, and an online fetch
 * failure are three distinct situations that must map to distinct messages; no Android runtime is
 * touched. (The Context-bound decisions , shouldReadCacheOnly / effectiveCacheMode , keep their
 * Robolectric coverage through the unchanged AppUtils wrappers in OfflineModeTest.)
 */
class OfflineReadPolicyTest {

  @Test
  fun emptyReason_explicitOfflineModeWinsRegardlessOfConnectivity() {
    assertEquals(OfflineEmptyReason.OFFLINE_MODE, OfflineRead.emptyReason(true, true))
    assertEquals(OfflineEmptyReason.OFFLINE_MODE, OfflineRead.emptyReason(true, false))
  }

  @Test
  fun emptyReason_noConnectivityIsDistinctFromOnlineError() {
    assertEquals(OfflineEmptyReason.NO_CONNECTION, OfflineRead.emptyReason(false, false))
    assertEquals(OfflineEmptyReason.ONLINE_ERROR, OfflineRead.emptyReason(false, true))
  }
}
