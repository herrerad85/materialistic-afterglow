/*
 * Copyright (c) 2015 Ha Duy Trung
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.growse.android.io.github.hidroh.materialistic.widget.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import androidx.cardview.widget.CardView;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.widget.TextView;

import com.google.android.material.color.DynamicColors;
import com.growse.android.io.github.hidroh.materialistic.AppUtils;
import com.growse.android.io.github.hidroh.materialistic.R;

public class ThemeView extends CardView {

    public ThemeView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ThemeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.theme_view, this, true);
        TypedArray idArray = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.id});
        boolean dynamicPreview = idArray.getResourceId(0, 0) == R.id.theme_dynamic;
        idArray.recycle();
        Context themed;
        if (dynamicPreview) {
            // Honest Material You preview: build from a CLEAN AppTheme.DayNight base (never the
            // host Activity theme, which could be carrying a Dark/Black overlay) and wrap it with
            // the wallpaper-derived dynamic palette. If dynamic is unavailable the swatch is GONE,
            // so wrapContextIfAvailable returning the unwrapped base here is harmless.
            ContextThemeWrapper base =
                    new ContextThemeWrapper(context.getApplicationContext(), R.style.AppTheme_DayNight);
            themed = DynamicColors.wrapContextIfAvailable(base);
        } else {
            TypedArray ta = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.theme});
            themed = new ContextThemeWrapper(context, ta.getResourceId(0, R.style.AppTheme));
            ta.recycle();
        }
        // Swatch fills with the theme's header colour (the toolbar reads ?attr/colorPrimary
        // everywhere), so the picker shows the actual theme colour rather than the muted card
        // surface. The Dark/Black/Solarized Dark headers are all grey900 by design, so those
        // three swatches read alike; the summary text below the picker names the selected theme.
        setCardBackgroundColor(
                AppUtils.getThemedColor(themed, R.attr.colorPrimary, Color.TRANSPARENT));
        ((TextView) findViewById(R.id.content)).setTextColor(
                AppUtils.getThemedColor(themed, android.R.attr.textColorTertiary, Color.BLACK));
        // Differentiate the themes whose header is the same grey900 (Dark / Black / Solarized Dark)
        // without misrepresenting that header: frame the swatch with the theme's own body surface
        // colour, which IS distinct (grey900 vs black vs solarized base03). A foreground
        // GradientDrawable border, not an overlay child (the prior body-strip child did not render
        // inside the CardView). Read colorSurfaceContainer directly rather than the colorCardBackground
        // alias: the Black theme has no parent to inherit that alias but does set colorSurfaceContainer.
        int bodyColor =
                AppUtils.getThemedColor(themed, R.attr.colorSurfaceContainer, Color.TRANSPARENT);
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.TRANSPARENT);
        border.setStroke(Math.round(getResources().getDisplayMetrics().density * 2f), bodyColor);
        border.setCornerRadius(getRadius());
        setForeground(border);
    }

}
