package org.communitypoke.settingsbrowser.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public final class Ui {

    private Ui() {
    }

    public static int dp(Context ctx, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                ctx.getResources().getDisplayMetrics());
    }

    public static LinearLayout.LayoutParams linearParams(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    public static LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    }

    /** Text color that tracks the Settings theme (dark/light aware). */
    public static int themedTextColor(Context ctx) {
        TypedValue tv = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true);
        int res = tv.resourceId != 0 ? tv.resourceId : android.R.color.primary_text_light;
        try {
            return ctx.getColor(res);
        } catch (Throwable ignored) {
            return Color.BLACK;
        }
    }
}
