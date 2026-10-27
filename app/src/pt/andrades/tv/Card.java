package pt.andrades.tv;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
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
 * quando está em foco. Na grelha mostra o nome por baixo; na doca, não.
 */
final class Card extends LinearLayout {
    interface OnFocus {
        void onFocus(Card card);
    }

    static final float FOCUS_SCALE = 1.12f;

    final App app;
    private final FrameLayout tile;
    private final View shine;
    private final TextView label;
    private View vpnDot;
    private OnFocus onFocus;

    Card(Context c, App app, int width, int height, Theme theme, boolean showLabel) {
        super(c);
        this.app = app;
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(true);
        setClickable(true);

        tile = new FrameLayout(c);
        round(tile, Ui.dp(c, 12));
        tile.setElevation(Ui.dp(c, 3));
        tile.addView(face(c, app, width, height), new FrameLayout.LayoutParams(-1, -1));
        addView(tile, new LayoutParams(width, height));

        if (!app.installed()) {
            TextView get = Ui.text(c, "Obter", 11, 0xFFFFFFFF);
            get.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(Ui.dp(c, 999));
            pill.setColor(0x99000000);
            get.setBackground(pill);
            int ph = (int) Ui.dp(c, 8), pv = (int) Ui.dp(c, 3);
            get.setPadding(ph, pv, ph, pv);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
            int m = (int) Ui.dp(c, 6);
            lp.setMargins(0, 0, m, m);
            tile.addView(get, lp);
        }

        // O brilho de vidro da Apple TV: uma luz suave no topo que só aparece em foco.
        shine = new View(c);
        shine.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0x55FFFFFF, 0x10FFFFFF, 0x00FFFFFF}));
        shine.setAlpha(0f);
        tile.addView(shine, new FrameLayout.LayoutParams(-1, -1));

        if (app.vpn) {
            vpnDot = new View(c);
            int s = (int) Ui.dp(c, 10);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(s, s, Gravity.TOP | Gravity.END);
            int m = (int) Ui.dp(c, 7);
            lp.setMargins(0, m, m, 0);
            tile.addView(vpnDot, lp);
            setVpn(false);
        }

        label = Ui.text(c, app.name, 14, theme.text);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setAlpha(0f);
        LayoutParams lp = new LayoutParams(width, -2);
        lp.topMargin = (int) Ui.dp(c, 12);
        if (showLabel) addView(label, lp);

        setPivotX(width / 2f);
        setPivotY(height / 2f);
    }

    /** O que se vê dentro do cartão (e, em grande, no destaque): o banner do app, o logótipo ou o ícone. */
    static View face(Context c, App app, int width, int height) {
        if (app.banner != null) {
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageDrawable(app.banner);
            return img;
        }
        FrameLayout f = new FrameLayout(c);
        if (app.logo != null) {
            f.setBackgroundColor(app.logo.bg);
            TextView t = new TextView(c);
            SpannableStringBuilder s = new SpannableStringBuilder();
            if (app.logo.mark != null) {
                s.append(app.logo.mark).append(' ');
                s.setSpan(new ForegroundColorSpan(app.logo.markColor), 0, app.logo.mark.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            s.append(app.logo.text);
            t.setText(s);
            t.setTextColor(app.logo.fg);
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, height * (app.logo.lowercase ? 0.26f : 0.24f));
            t.setTypeface(Typeface.create(app.logo.lowercase ? "sans-serif-medium" : "sans-serif-black",
                    Typeface.NORMAL));
            t.setLetterSpacing(app.logo.lowercase ? -0.02f : -0.01f);
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER);
            f.addView(t, new FrameLayout.LayoutParams(-1, -1));
        } else {
            f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[] {app.colorTop, app.colorBottom}));
            if (app.icon != null) {
                ImageView img = new ImageView(c);
                img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                img.setImageDrawable(app.icon);
                int s = (int) (height * 0.5f);
                f.addView(img, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
            }
        }
        return f;
    }

    static void round(View v, float radius) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        v.setClipToOutline(true);
    }

    void setOnFocus(OnFocus onFocus) {
        this.onFocus = onFocus;
    }

    void setVpn(boolean on) {
        if (vpnDot == null) return;
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(on ? 0xFF34C759 : 0xFFB0B0B5);
        d.setStroke((int) Ui.dp(getContext(), 2), 0xE6FFFFFF);
        vpnDot.setBackground(d);
    }

    @Override protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        float s = focused ? FOCUS_SCALE : 1f;
        animate().scaleX(s).scaleY(s).setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
        tile.animate().translationZ(focused ? Ui.dp(getContext(), 18) : 0).setDuration(200).start();
        shine.animate().alpha(focused ? 1f : 0f).setDuration(200).start();
        label.animate().alpha(focused ? 1f : 0f).translationY(focused ? Ui.dp(getContext(), 6) : 0)
                .setDuration(200).start();
        if (focused && onFocus != null) onFocus.onFocus(this);
    }
}
