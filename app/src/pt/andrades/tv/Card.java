package pt.andrades.tv;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Um cartão ao estilo da Apple TV: ícone 16:9 de cantos arredondados que cresce e ganha sombra e brilho
 * quando está em foco, com o nome do app a aparecer por baixo.
 */
final class Card extends LinearLayout {
    interface OnFocus {
        void onFocus(Card card);
    }

    static final float FOCUS_SCALE = 1.14f;

    final App app;
    private final FrameLayout tile;
    private final View shine;
    private final TextView label;
    private View vpnDot;
    private OnFocus onFocus;

    Card(Context c, App app, int width, int height) {
        super(c);
        this.app = app;
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(true);
        setClickable(true);

        float radius = Ui.dp(c, 14);
        tile = new FrameLayout(c);
        tile.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        tile.setClipToOutline(true);
        tile.setElevation(Ui.dp(c, 2));
        addView(tile, new LayoutParams(width, height));

        if (app.banner != null) {
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageDrawable(app.banner);
            tile.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            tile.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[] {Catalog.darken(app.colorTop, 1.15f), app.colorBottom}));
            if (app.installed() && app.icon != null) {
                ImageView img = new ImageView(c);
                img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                img.setImageDrawable(app.icon);
                int s = (int) (height * 0.52f);
                tile.addView(img, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
            } else {
                LinearLayout text = new LinearLayout(c);
                text.setOrientation(VERTICAL);
                text.setGravity(Gravity.CENTER);
                TextView name = Ui.text(c, app.name, 20, 0xFFFFFFFF);
                name.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                name.setGravity(Gravity.CENTER);
                text.addView(name);
                if (!app.installed()) {
                    TextView hint = Ui.text(c, app.sideload ? "Instalar pelo computador" : "Instalar", 11, 0xB3FFFFFF);
                    hint.setGravity(Gravity.CENTER);
                    hint.setPadding(0, (int) Ui.dp(c, 4), 0, 0);
                    text.addView(hint);
                }
                tile.addView(text, new FrameLayout.LayoutParams(-1, -1));
            }
        }

        // O "brilho" de vidro da Apple TV: uma luz suave no topo que só aparece em foco.
        shine = new View(c);
        shine.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x40FFFFFF, 0x0DFFFFFF, 0x00FFFFFF}));
        shine.setAlpha(0f);
        tile.addView(shine, new FrameLayout.LayoutParams(-1, -1));

        if (app.vpn) {
            vpnDot = new View(c);
            int s = (int) Ui.dp(c, 12);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(s, s, Gravity.TOP | Gravity.END);
            lp.setMargins(0, (int) Ui.dp(c, 8), (int) Ui.dp(c, 8), 0);
            tile.addView(vpnDot, lp);
            setVpn(false);
        }

        label = Ui.text(c, app.name, 14, 0xFFFFFFFF);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setAlpha(0f);
        LayoutParams lp = new LayoutParams(width, -2);
        lp.topMargin = (int) Ui.dp(c, 16);
        addView(label, lp);

        setPivotX(width / 2f);
        setPivotY(height / 2f);
    }

    void setOnFocus(OnFocus onFocus) {
        this.onFocus = onFocus;
    }

    void setVpn(boolean on) {
        if (vpnDot == null) return;
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(on ? 0xFF34C759 : 0xFF8E8E93);
        d.setStroke((int) Ui.dp(getContext(), 2), 0xCCFFFFFF);
        vpnDot.setBackground(d);
    }

    @Override protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        float s = focused ? FOCUS_SCALE : 1f;
        animate().scaleX(s).scaleY(s).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
        tile.animate().translationZ(focused ? Ui.dp(getContext(), 22) : 0).setDuration(180).start();
        shine.animate().alpha(focused ? 1f : 0f).setDuration(180).start();
        label.animate().alpha(focused ? 1f : 0f).translationY(focused ? Ui.dp(getContext(), 4) : 0)
                .setDuration(180).start();
        if (focused && onFocus != null) onFocus.onFocus(this);
    }
}
