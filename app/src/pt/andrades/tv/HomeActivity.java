package pt.andrades.tv;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * O ecrã inicial, ao estilo da Apple TV. O primeiro ecrã tem a saudação e o relógio, um destaque grande do app
 * em foco e, em baixo, a doca de vidro com os favoritos. Ao descer aparecem todos os apps da TV numa grelha.
 */
public final class HomeActivity extends Activity implements VpnWatcher.Listener {
    private static final int DOCK_SLOTS = 5;
    private static final int GRID_COLUMNS = 6;
    private static final String UPDATE = "andrades.update";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Card> cards = new ArrayList<>();
    private final ArgbEvaluator argb = new ArgbEvaluator();

    private Theme theme;
    private Catalog.Result catalog;
    private VpnWatcher vpn;
    private String focusedPkg;
    private Updater.Release update;
    private boolean installing;

    private FrameLayout root;
    private ScrollView scroll;
    private GradientDrawable glow, heroBg;
    private int colorTop = 0xFF8E8E93, colorBottom = 0xFF3A3A3C;
    private ValueAnimator colorAnim;
    private FrameLayout heroFace;
    private TextView greeting, date, clock, vpnPill, title, subtitle;
    private int heroFaceW, heroFaceH;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            if (clock != null) {
                clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
                date.setText(Ui.longDate(now) + (update != null ? "  ·  Atualização disponível em Todas as apps" : ""));
                greeting.setText(Theme.greeting());
            }
            // Às 7h e às 20h o tema muda entre claro e escuro.
            Theme t = Theme.now();
            if (theme != null && t.dark != theme.dark && catalog != null) {
                theme = t;
                build();
            }
            main.postDelayed(this, 60_000 - (System.currentTimeMillis() % 60_000));
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        theme = Theme.now();
        root = new FrameLayout(this);
        setContentView(root);
        vpn = new VpnWatcher(this, this);
    }

    @Override protected void onResume() {
        super.onResume();
        vpn.start();
        main.removeCallbacks(tick);
        main.post(tick);
        worker.execute(() -> {
            Catalog.Result r = Catalog.load(this);
            Updater.Release u = Updater.check(this);
            main.post(() -> {
                if (isFinishing()) return;
                catalog = r;
                update = u;
                theme = Theme.now();
                build();
            });
        });
    }

    @Override protected void onPause() {
        super.onPause();
        vpn.stop();
        main.removeCallbacks(tick);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    /** O launcher não sai com "Voltar": volta só ao topo. */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            toTop();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        toTop();
    }

    private void toTop() {
        if (!cards.isEmpty()) cards.get(0).requestFocus();
        if (scroll != null) scroll.smoothScrollTo(0, 0);
    }

    private int dp(float v) {
        return (int) Ui.dp(this, v);
    }

    private void build() {
        root.removeAllViews();
        cards.clear();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = dm.widthPixels, h = dm.heightPixels;
        int pad = dp(56);

        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {theme.bgTop, theme.bgBottom}));

        // Um brilho suave com as cores do app em foco, por trás de tudo.
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(w * 0.75f);
        glow.setGradientCenter(0.5f, 0.35f);
        glow.setColors(new int[] {withAlpha(colorTop, theme.dark ? 0x55 : 0x40), 0x00000000});
        View glowView = new View(this);
        glowView.setBackground(glow);
        root.addView(glowView, new FrameLayout.LayoutParams(-1, -1));

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(pad, 0, pad, dp(56));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        // --- Primeiro ecrã: cabeçalho, destaque, legenda e doca, a ocupar exatamente a altura da TV.
        int gap = dp(22), dockPad = dp(16);
        int dockCardW = (w - 2 * pad - 2 * dockPad - (DOCK_SLOTS - 1) * gap) / DOCK_SLOTS;
        int dockCardH = dockCardW * 9 / 16;
        int headerH = dp(104), captionH = dp(72), dockH = dockCardH + 2 * dockPad, bottom = dp(28);
        int heroH = h - headerH - captionH - dockH - bottom;

        LinearLayout first = new LinearLayout(this);
        first.setOrientation(LinearLayout.VERTICAL);
        first.setClipChildren(false);
        first.setClipToPadding(false);
        first.addView(header(), new LinearLayout.LayoutParams(-1, headerH));
        first.addView(hero(heroH), new LinearLayout.LayoutParams(-1, heroH));
        first.addView(caption(), new LinearLayout.LayoutParams(-1, captionH));
        first.addView(dock(dockCardW, dockCardH, gap, dockPad), new LinearLayout.LayoutParams(-1, dockH));
        content.addView(first, new LinearLayout.LayoutParams(-1, h - bottom));

        // --- Todas as apps.
        TextView all = Ui.text(this, "Todas as apps", 22, theme.text);
        all.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        all.setPadding(0, dp(64), 0, dp(24));
        content.addView(all);
        int gridGap = dp(24);
        int gridW = (w - 2 * pad - (GRID_COLUMNS - 1) * gridGap) / GRID_COLUMNS;
        int gridH = gridW * 9 / 16;
        List<App> others = new ArrayList<>(catalog.others);
        if (update != null) {
            others.add(0, new App(UPDATE, "Atualizar", "Andrades TV 1." + update.version + " · carregue OK para instalar",
                    0xFF34C759, 0xFF0B4A1E, new App.Logo(0xFF30B455, 0xFFFFFFFF, "Atualizar", "↓", 0xFFFFFFFF, false),
                    null, null, new Intent(), false, false));
        }
        for (int i = 0; i < others.size(); i += GRID_COLUMNS) {
            LinearLayout row = new LinearLayout(this);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            row.setPadding(0, 0, 0, dp(18));
            List<App> slice = others.subList(i, Math.min(i + GRID_COLUMNS, others.size()));
            for (int j = 0; j < slice.size(); j++) {
                Card card = card(slice.get(j), gridW, gridH, true);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                if (j > 0) lp.leftMargin = gridGap;
                row.addView(card, lp);
            }
            content.addView(row);
        }

        main.removeCallbacks(tick);
        tick.run();
        onVpnChanged(vpn.isOn());
        Card target = cards.isEmpty() ? null : cards.get(0);
        for (Card c : cards) if (c.app.pkg.equals(focusedPkg)) target = c;
        if (target != null) target.requestFocus();
    }

    private View header() {
        FrameLayout f = new FrameLayout(this);
        f.setPadding(0, dp(34), 0, 0);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        greeting = Ui.text(this, Theme.greeting(), 34, theme.text);
        greeting.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        date = Ui.text(this, "", 15, theme.textDim);
        date.setPadding(0, dp(6), 0, 0);
        left.addView(greeting);
        left.addView(date);
        f.addView(left, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.TOP));

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.CENTER_VERTICAL);
        vpnPill = Ui.text(this, "", 14, theme.text);
        vpnPill.setPadding(dp(14), dp(8), dp(14), dp(8));
        right.addView(vpnPill);
        clock = Ui.text(this, "", 30, theme.text);
        clock.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(20);
        right.addView(clock, lp);
        f.addView(right, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP));
        return f;
    }

    /** O destaque: um painel grande com as cores do app em foco e o logótipo dele ao centro. */
    private View hero(int heroH) {
        FrameLayout panel = new FrameLayout(this);
        Card.round(panel, dp(26));
        panel.setElevation(dp(6));
        heroBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {colorTop, colorBottom});
        panel.setBackground(heroBg);

        View light = new View(this);
        light.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x30FFFFFF, 0x00FFFFFF, 0x26000000}));
        panel.addView(light, new FrameLayout.LayoutParams(-1, -1));

        heroFaceH = (int) (heroH * 0.52f);
        heroFaceW = heroFaceH * 16 / 9;
        heroFace = new FrameLayout(this);
        Card.round(heroFace, dp(18));
        heroFace.setElevation(dp(16));
        panel.addView(heroFace, new FrameLayout.LayoutParams(heroFaceW, heroFaceH, Gravity.CENTER));
        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        return wrap;
    }

    private View caption() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(4), dp(16), 0, 0);
        title = Ui.text(this, "", 22, theme.text);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        subtitle = Ui.text(this, "", 15, theme.textDim);
        subtitle.setPadding(0, dp(6), 0, 0);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(title);
        c.addView(subtitle);
        return c;
    }

    /** A doca de vidro fosco com os favoritos. */
    private View dock(int cw, int ch, int gap, int dockPad) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        bar.setPadding(dockPad, dockPad, dockPad, dockPad);
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(dp(30));
        glass.setColor(theme.glass);
        glass.setStroke(dp(1.5f), theme.glassStroke);
        bar.setBackground(glass);
        for (int i = 0; i < catalog.favorites.size(); i++) {
            Card card = card(catalog.favorites.get(i), cw, ch, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.leftMargin = gap;
            bar.addView(card, lp);
        }
        return bar;
    }

    private Card card(App app, int width, int height, boolean inGrid) {
        Card card = new Card(this, app, width, height, theme, inGrid);
        card.setOnClickListener(v -> open(app));
        card.setOnFocus(c -> {
            boolean changed = !app.pkg.equals(focusedPkg) || heroFace.getChildCount() == 0;
            focusedPkg = app.pkg;
            if (changed) showHero(app);
            if (!inGrid) {
                scroll.smoothScrollTo(0, 0);
            } else {
                // Na grelha, a linha em foco fica a meio do ecrã, com espaço para o zoom e o nome.
                int y = ((View) c.getParent()).getTop() + height / 2;
                scroll.smoothScrollTo(0, Math.max(0, y - scroll.getHeight() / 2));
            }
        });
        cards.add(card);
        return card;
    }

    private void showHero(App app) {
        title.setText(app.name);
        subtitle.setText(describe(app));

        heroFace.removeAllViews();
        heroFace.addView(Card.face(this, app, heroFaceW, heroFaceH), new FrameLayout.LayoutParams(-1, -1));
        heroFace.setAlpha(0f);
        heroFace.setScaleX(0.96f);
        heroFace.setScaleY(0.96f);
        heroFace.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(new DecelerateInterpolator()).start();

        if (colorAnim != null) colorAnim.cancel();
        int fromTop = colorTop, fromBottom = colorBottom;
        colorAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(420);
        colorAnim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            colorTop = (int) argb.evaluate(f, fromTop, app.colorTop);
            colorBottom = (int) argb.evaluate(f, fromBottom, app.colorBottom);
            heroBg.setColors(new int[] {colorTop, colorBottom});
            glow.setColors(new int[] {withAlpha(colorTop, theme.dark ? 0x55 : 0x40), 0x00000000});
        });
        colorAnim.start();
    }

    private String describe(App app) {
        if (!app.installed()) {
            return app.sideload
                    ? "Não instalado. No computador: ./tools/instalar.sh <IP da TV> --smarttube"
                    : "Não instalado. Carregue OK para obter na Play Store.";
        }
        if (app.vpn) {
            return vpn.isOn() ? "Ligada · a sua ligação está protegida" : "Desligada · carregue OK para ligar";
        }
        return app.tagline != null ? app.tagline : "";
    }

    private void open(App app) {
        if (UPDATE.equals(app.pkg)) {
            installUpdate();
            return;
        }
        try {
            if (app.installed()) {
                startActivity(app.launch);
            } else if (app.sideload) {
                Toast.makeText(this, "Instale pelo computador: ./tools/instalar.sh <IP da TV> --smarttube",
                        Toast.LENGTH_LONG).show();
            } else {
                startActivity(Catalog.storeIntent(app.pkg));
            }
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Não foi possível abrir " + app.name, Toast.LENGTH_SHORT).show();
        }
    }

    private void installUpdate() {
        if (installing || update == null) return;
        if (!Updater.canInstall(this)) {
            Toast.makeText(this, "Autorize o Andrades TV a instalar apps e carregue OK outra vez.", Toast.LENGTH_LONG).show();
            try {
                startActivity(Updater.permissionIntent(this));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "Esta TV não tem esse ecrã. No computador: ./tools/instalar.sh <IP da TV>",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        installing = true;
        Toast.makeText(this, "A descarregar a atualização…", Toast.LENGTH_SHORT).show();
        Updater.Release r = update;
        worker.execute(() -> {
            try {
                Updater.install(this, r);
            } catch (Exception e) {
                main.post(() -> Toast.makeText(this, "A atualização falhou: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                installing = false;
            }
        });
    }

    @Override public void onVpnChanged(boolean on) {
        if (vpnPill == null) return;
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(999));
        pill.setColor(on ? 0x4034C759 : theme.glass);
        pill.setStroke(dp(1), on ? 0x8034C759 : theme.glassStroke);
        vpnPill.setBackground(pill);
        vpnPill.setText(on ? "●  VPN ligada" : "○  VPN desligada");
        for (Card c : cards) {
            c.setVpn(on);
            if (c.app.vpn && c.isFocused()) subtitle.setText(describe(c.app));
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
