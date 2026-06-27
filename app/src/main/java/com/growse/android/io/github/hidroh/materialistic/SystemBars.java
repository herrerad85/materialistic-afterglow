/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.growse.android.io.github.hidroh.materialistic;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Edge-to-edge (Android 15+) system-bar inset helpers, extracted from AppUtils. AppUtils keeps thin
 * compatibility wrappers (padTopSystemBars / padBottomSystemBars / padVerticalSystemBars /
 * marginBottomSystemBars) that delegate here, so the View call sites are unchanged.
 */
public final class SystemBars {

    private SystemBars() {}

    /** Pads view's top by the status-bar/cutout inset. For toolbars on non-CoordinatorLayout screens
     *  (CoordinatorLayout screens use fitsSystemWindows instead). */
    public static void padTop(View view) {
        pad(view, true, false, false);
    }

    /** Pads view's bottom by the nav-bar inset (+ IME when includeIme) so content/controls are not
     *  occluded under the transparent system bars. */
    public static void padBottom(View view, boolean includeIme) {
        pad(view, false, true, includeIme);
    }

    /** Pads view's top AND bottom in a single listener. Use this instead of stacking padTop + padBottom
     *  on the same view (which would clobber, see pad). */
    public static void padVertical(View view, boolean includeIme) {
        pad(view, true, true, includeIme);
    }

    /** Shared impl: every pad* helper delegates here so a view ends up with exactly ONE
     *  OnApplyWindowInsetsListener. ViewCompat.setOnApplyWindowInsetsListener REPLACES any prior
     *  listener, so two helpers on the same view clobber each other; pad both edges in one call.
     *  The horizontal (left/right) inset is ALWAYS applied: in landscape the side nav bar or a display
     *  cutout would otherwise occlude edge-aligned content (e.g. preference switches under a 3-button
     *  nav bar). It is zero in portrait and under gesture nav, so this is a no-op there. The top/bottom
     *  flags only gate the vertical insets. */
    private static void pad(View view, boolean top, boolean bottom, boolean includeIme) {
        final int l = view.getPaddingLeft(), t = view.getPaddingTop(),
                r = view.getPaddingRight(), b = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int mask = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();
            if (includeIme) mask |= WindowInsetsCompat.Type.ime();
            Insets i = insets.getInsets(mask);
            v.setPadding(l + i.left, top ? t + i.top : t, r + i.right, bottom ? b + i.bottom : b);
            return insets;
        });
    }

    /** Adds the nav-bar inset to view's bottom margin (for FABs / floating controls). */
    public static void marginBottom(View view) {
        final ViewGroup.MarginLayoutParams base = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        final int bottom = base.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets i = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.bottomMargin = bottom + i.bottom;
            v.setLayoutParams(lp);
            return insets;
        });
    }
}
