/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data;

import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.os.Parcelable;
import com.growse.android.io.github.hidroh.materialistic.widget.SinglePageItemRecyclerViewAdapter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Pre-public G2 saved-state audit (measured with a real Parcel, not estimated). A saved-state bundle
 * must stay well under the ~1 MB Binder transaction limit; the audit target is 200 KB per save.
 *
 * Single HackerNewsItem saves (ItemActivity STATE_ITEM, BaseListActivity STATE_SELECTED_ITEM) are
 * bounded by one comment/story and measure tiny: safe. BaseListFragment persists only an int
 * (selection position): safe by inspection. ItemFragment used to persist the whole flattened comment
 * tree (an ArrayList of every loaded comment, each carrying its full text body) under
 * state:adapterItems, which scales with thread size and crosses the budget for large threads;
 * commentTreeSave_scalesPastBudgetForLargeThreads keeps measuring that to document why G2b removed the
 * persistence (the thread now reloads from the root item on recreate). See ItemSavedStateTest for the
 * regression guard that the heavy key is gone.
 */
@RunWith(RobolectricTestRunner.class)
public class SavedStateParcelSizeTest {

  private static final int BUDGET_BYTES = 200 * 1024;

  private static String repeat(char c, int n) {
    char[] a = new char[n];
    Arrays.fill(a, c);
    return new String(a);
  }

  /** A HackerNewsItem with a text body of the given length; only the size-relevant field is set. */
  private static HackerNewsItem comment(long id, int textChars) throws Exception {
    HackerNewsItem item = new HackerNewsItem(id);
    Field text = HackerNewsItem.class.getDeclaredField("text");
    text.setAccessible(true);
    text.set(item, repeat('x', textChars));
    return item;
  }

  private static int parcelSize(Parcelable p) {
    Parcel parcel = Parcel.obtain();
    p.writeToParcel(parcel, 0);
    int size = parcel.dataSize();
    parcel.recycle();
    return size;
  }

  @Test
  public void singleItemSave_isWellUnderBudget() throws Exception {
    // ItemActivity / BaseListActivity persist one item; even a generous 2 KB comment body is tiny.
    int size = parcelSize(comment(1L, 2000));
    assertTrue("single item save measured " + size + " bytes, must be under budget", size < BUDGET_BYTES);
  }

  @Test
  public void commentTreeSave_scalesPastBudgetForLargeThreads() throws Exception {
    // ItemFragment persists the whole comment tree. A large thread (500 comments, ~400 chars each)
    // crosses the 200 KB budget, which is why it is flagged as a follow-up rather than left silent.
    ArrayList<Item> list = new ArrayList<>();
    for (int i = 0; i < 500; i++) {
      list.add(comment(i, 400));
    }
    SinglePageItemRecyclerViewAdapter.SavedState saved =
        new SinglePageItemRecyclerViewAdapter.SavedState(list);
    int size = parcelSize(saved);
    assertTrue("comment tree of 500 measured " + size + " bytes, expected over budget", size > BUDGET_BYTES);
  }
}
