/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Regression guard for the comment-tree TransactionTooLargeException (pre-public G2b). ItemFragment
 * used to persist the whole flattened comment tree (mAdapterItems, every loaded comment with its full
 * text) under state:adapterItems, which a real-Parcel audit (SavedStateParcelSizeTest) measured over
 * the 200 KB budget for large threads, the same bug class as the UserActivity user save. The fix stops
 * persisting it; on recreate the thread reloads from the restored root item and rebuilds the tree.
 *
 * ItemFragment is a Hilt entry point with no Hilt test harness in this module, so it is not driven
 * through a lifecycle (which would inject). Its onSaveInstanceState is public and the Fragment base
 * implementation is a no-op, so a bare instance can be saved directly: the heavy comment-tree key must
 * never be written, while the light single-item keys are still kept.
 */
@RunWith(RobolectricTestRunner.class)
public class ItemSavedStateTest {

  @Test
  public void onSaveInstanceState_doesNotPersistCommentTree() {
    ItemFragment fragment = new ItemFragment();
    Bundle outState = new Bundle();
    fragment.onSaveInstanceState(outState);
    assertFalse(
        "ItemFragment must not persist the comment tree (state:adapterItems) into saved state",
        outState.containsKey("state:adapterItems"));
    // The light keys are kept: the root item and its id are small and let the thread reload on recreate.
    assertTrue(outState.containsKey("state:item"));
    assertTrue(outState.containsKey("state:itemId"));
  }
}
