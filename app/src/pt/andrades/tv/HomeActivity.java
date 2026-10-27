package pt.andrades.tv;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
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
 * O ecrã inicial. Em cima, a "prateleira": um fundo com as cores do app em foco, o nome e uma frase sobre ele.
 * Em baixo, a doca com os favoritos e, ao descer, todos os apps da TV numa grelha.
 */
public final class HomeActivity extends Activity implements VpnWatcher.Listener {
    private static final int COLUMNS = 5;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Card> cards = new ArrayList<>();
    private final ArgbEvaluator argb = new ArgbEvaluator();

    private GradientDrawable heroBg;
    private int heroTop = 0xFF1C1F26, heroBottom = 0xFF08090C;
    private ValueAnimator heroAnim;
    private View hero;
    private ScrollView scroll;
    private LinearLayout content;
    private TextView title, subtitle, clock, date, vpnChip;
    private VpnWatcher vpn;
    private String focusedPkg;
    private int heroHeight;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
            date.setText(new SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault()).format(now));
            main.postDelayed(this, 60_000 - (System.currentTimeMillis() % 60_000));
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        heroHeight = (int) (dm.heightPixels * 0.50f);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        hero = new View(this);
        heroBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {heroTop, heroBottom, 0xFF000000});
        hero.setBackground(heroBg);
        root.addView(hero, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00000000, 0x33000000, 0xCC000000, 0xF2000000}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setSmoothScrollingEnabled(true);
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) ->
                hero.setAlpha(1f - Math.min(1f, y / (float) heroHeight) * 0.65f));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        int pad = (int) Ui.dp(this, 64);
        content.setPadding(pad, 0, pad, pad);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        root.addView(topBar(), new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        setContentView(root);
        vpn = new VpnWatcher(this, this);
        main.post(tick);
    }

    /** Relógio, data e o estado da VPN, no canto superior direito. */
    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        int p = (int) Ui.dp(this, 40);
        bar.setPadding(p, (int) Ui.dp(this, 32), (int) Ui.dp(this, 64), 0);

        vpnChip = Ui.text(this, "", 13, 0xFFFFFFFF);
        vpnChip.setPadding((int) Ui.dp(this, 12), (int) Ui.dp(this, 6), (int) Ui.dp(this, 12), (int) Ui.dp(this, 6));
        bar.addView(vpnChip);

        LinearLayout time = new LinearLayout(this);
        time.setOrientation(LinearLayout.VERTICAL);
        time.setGravity(Gravity.END);
        clock = Ui.text(this, "", 30, 0xFFFFFFFF);
        clock.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        date = Ui.text(this, "", 13, 0xB3FFFFFF);
        date.setPadding(0, (int) Ui.dp(this, 4), 0, 0);
        time.addView(clock);
        time.addView(date);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = (int) Ui.dp(this, 24);
        bar.addView(time, lp);
        onVpnChanged(false);
        return bar;
    }

    @Override protected void onResume() {
        super.onResume();
        vpn.start();
        reload();
    }

    @Override protected void onPause() {
        super.onPause();
        vpn.stop();
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(tick);
        worker.shutdownNow();
        super.onDestroy();
    }

    /** O launcher não sai com "Voltar": volta só ao topo. */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!cards.isEmpty()) cards.get(0).requestFocus();
            scroll.smoothScrollTo(0, 0);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        // Botão Home com o launcher já aberto: voltar ao primeiro favorito.
        if (!cards.isEmpty()) cards.get(0).requestFocus();
        scroll.smoothScrollTo(0, 0);
    }

    private void reload() {
        worker.execute(() -> {
            Catalog.Result r = Catalog.load(this);
            main.post(() -> { if (!isFinishing()) build(r); });
        });
    }

    private void build(Catalog.Result r) {
        content.removeAllViews();
        cards.clear();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int gap = (int) Ui.dp(this, 36);
        int width = (dm.widthPixels - content.getPaddingLeft() - content.getPaddingRight() - gap * (COLUMNS - 1)) / COLUMNS;
        int height = width * 9 / 16;

        // A prateleira: o título e a frase do app em foco, colados à doca.
        LinearLayout shelf = new LinearLayout(this);
        shelf.setOrientation(LinearLayout.VERTICAL);
        shelf.setGravity(Gravity.BOTTOM);
        title = Ui.text(this, "", 44, 0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        subtitle = Ui.text(this, "", 18, 0xBFFFFFFF);
        subtitle.setPadding(0, (int) Ui.dp(this, 10), 0, 0);
        shelf.addView(title);
        shelf.addView(subtitle);
        shelf.setPadding(0, 0, 0, (int) Ui.dp(this, 36));
        content.addView(shelf, new LinearLayout.LayoutParams(-1, heroHeight));

        content.addView(row(r.favorites, width, height, gap, true));

        TextView all = Ui.text(this, "Todas as apps", 20, 0xB3FFFFFF);
        all.setPadding(0, (int) Ui.dp(this, 56), 0, (int) Ui.dp(this, 24));
        content.addView(all);
        for (int i = 0; i < r.others.size(); i += COLUMNS) {
            LinearLayout row = row(r.others.subList(i, Math.min(i + COLUMNS, r.others.size())), width, height, gap, false);
            row.setPadding(0, 0, 0, (int) Ui.dp(this, 20));
            content.addView(row);
        }

        Card target = cards.isEmpty() ? null : cards.get(0);
        for (Card c : cards) if (c.app.pkg.equals(focusedPkg)) target = c;
        if (target != null) target.requestFocus();
        boolean on = vpn.isOn();
        for (Card c : cards) c.setVpn(on);
    }

    private LinearLayout row(List<App> apps, int width, int height, int gap, boolean dock) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        for (int i = 0; i < apps.size(); i++) {
            Card card = new Card(this, apps.get(i), width, height);
            card.setOnClickListener(v -> open(card.app));
            card.setOnFocus(c -> {
                focusedPkg = c.app.pkg;
                showShelf(c.app);
                if (dock) scroll.smoothScrollTo(0, 0);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.leftMargin = gap;
            row.addView(card, lp);
            cards.add(card);
        }
        return row;
    }

    private void showShelf(App app) {
        title.setText(app.name);
        subtitle.setText(describe(app));
        if (heroAnim != null) heroAnim.cancel();
        int fromTop = heroTop, fromBottom = heroBottom;
        heroAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(420);
        heroAnim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            heroTop = (int) argb.evaluate(f, fromTop, app.colorTop);
            heroBottom = (int) argb.evaluate(f, fromBottom, app.colorBottom);
            heroBg.setColors(new int[] {heroTop, heroBottom, 0xFF000000});
        });
        heroAnim.start();
    }

    private String describe(App app) {
        if (!app.installed()) {
            return app.sideload
                    ? "Não instalado. No computador: ./tools/instalar.sh <IP da TV> --smarttube"
                    : "Não instalado. Carregue OK para abrir a Play Store.";
        }
        if (app.vpn) {
            return vpn.isOn() ? "Ligada: a sua ligação está protegida." : "Desligada. Carregue OK para ligar.";
        }
        return app.tagline != null ? app.tagline : "";
    }

    private void open(App app) {
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

    @Override public void onVpnChanged(boolean on) {
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(Ui.dp(this, 999));
        chip.setColor(on ? 0x5934C759 : 0x40FFFFFF);
        vpnChip.setBackground(chip);
        vpnChip.setText(on ? "●  VPN ligada" : "○  VPN desligada");
        for (Card c : cards) {
            c.setVpn(on);
            if (c.app.vpn && c.isFocused()) subtitle.setText(describe(c.app));
        }
    }
}
