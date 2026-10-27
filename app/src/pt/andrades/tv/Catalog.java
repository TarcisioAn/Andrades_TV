package pt.andrades.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lê os apps da TV: primeiro os favoritos (pela ordem abaixo), depois todos os outros por ordem alfabética. */
final class Catalog {
    private Catalog() {}

    /** Um favorito fixo. Vários pacotes porque o mesmo serviço muda de nome de pacote entre versões e regiões. */
    private static final class Fav {
        final String name, tagline;
        final int top, bottom;
        final App.Logo logo;
        final boolean sideload, vpn;
        final String[] pkgs;

        Fav(String name, String tagline, int top, int bottom, App.Logo logo, boolean sideload, boolean vpn,
            String... pkgs) {
            this.name = name;
            this.tagline = tagline;
            this.top = top;
            this.bottom = bottom;
            this.logo = logo;
            this.sideload = sideload;
            this.vpn = vpn;
            this.pkgs = pkgs;
        }
    }

    private static final Fav[] FAVORITES = {
        new Fav("HBO Max", "Séries e filmes da HBO, Warner Bros. e DC", 0xFF5B34F2, 0xFF1B0F5E,
                new App.Logo(0xFF4A2BD8, 0xFFFFFFFF, "HBO max", null, 0, false),
                false, false, "com.wbd.stream", "com.hbo.hbonow"),
        new Fav("Disney+", "Disney, Pixar, Marvel, Star Wars e National Geographic", 0xFF1F4FB5, 0xFF061538,
                new App.Logo(0xFF0B2260, 0xFFFFFFFF, "Disney+", null, 0, false),
                false, false, "com.disney.disneyplus"),
        new Fav("Prime Video", "Filmes e séries da Amazon", 0xFF1A98FF, 0xFF06345A,
                new App.Logo(0xFF1A98FF, 0xFFFFFFFF, "prime video", null, 0, true),
                false, false, "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
        new Fav("YouTube", "Sem anúncios e sem patrocínios, com o SmartTube", 0xFFE5262C, 0xFF4A0709,
                new App.Logo(0xFFFFFFFF, 0xFF0F0F0F, "YouTube", "▶", 0xFFFF0033, false),
                true, false, "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv",
                "com.liskovsoft.smarttubetv.beta"),
        new Fav("NordVPN", "Proteja a ligação antes de ver", 0xFF4A7BFF, 0xFF0E1E52,
                new App.Logo(0xFF3E66F5, 0xFFFFFFFF, "NordVPN", null, 0, false),
                false, true, "com.nordvpn.android"),
    };

    /** Apps que não fazem sentido no ecrã inicial (o próprio launcher, o da fábrica e afins). */
    private static final Set<String> HIDDEN = new HashSet<>();
    static {
        HIDDEN.add("pt.andrades.tv");
        HIDDEN.add("com.mitv.tvhome");
        HIDDEN.add("com.google.android.tvlauncher");
        HIDDEN.add("com.google.android.apps.tv.launcherx");
        HIDDEN.add("com.android.tv.settings");
    }

    static final class Result {
        final List<App> favorites = new ArrayList<>();
        final List<App> others = new ArrayList<>();
    }

    static Result load(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, ResolveInfo> launchable = new LinkedHashMap<>();
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!launchable.containsKey(pkg)) launchable.put(pkg, ri);
            }
        }

        Result r = new Result();
        Set<String> used = new HashSet<>(HIDDEN);
        for (Fav f : FAVORITES) {
            String found = null;
            for (String p : f.pkgs) {
                if (launchable.containsKey(p)) { found = p; break; }
            }
            if (found != null) {
                ApplicationInfo ai = launchable.get(found).activityInfo.applicationInfo;
                r.favorites.add(new App(found, f.name, f.tagline, f.top, f.bottom, f.logo,
                        ai.loadBanner(pm), ai.loadIcon(pm), launchIntent(pm, found), f.sideload, f.vpn));
            } else {
                r.favorites.add(new App(f.pkgs[0], f.name, f.tagline, f.top, f.bottom, f.logo,
                        null, null, null, f.sideload, f.vpn));
            }
            for (String p : f.pkgs) used.add(p);
        }

        List<App> others = new ArrayList<>();
        for (Map.Entry<String, ResolveInfo> e : launchable.entrySet()) {
            String pkg = e.getKey();
            if (used.contains(pkg)) continue;
            Intent launch = launchIntent(pm, pkg);
            if (launch == null) continue;
            ResolveInfo ri = e.getValue();
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            Drawable icon = ri.loadIcon(pm);
            int tint = averageColor(icon);
            others.add(new App(pkg, String.valueOf(ri.loadLabel(pm)), null,
                    darken(tint, 0.8f), darken(tint, 0.3f), null,
                    ai.loadBanner(pm), icon, launch, false, false));
        }
        Collator col = Collator.getInstance();
        others.sort((a, b) -> col.compare(a.name, b.name));
        r.others.addAll(others);

        r.others.add(new App("android.settings", "Definições", "Rede, imagem, som e sistema da TV",
                0xFF6E7380, 0xFF23262D, null, null, c.getDrawable(android.R.drawable.ic_menu_preferences),
                new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), false, false));
        return r;
    }

    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }

    /** A página do app na Play Store (ou no navegador, se a TV não tiver a loja). */
    static Intent storeIntent(String pkg) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Cor média do ícone, para pintar o fundo dos apps que não são favoritos. */
    private static int averageColor(Drawable d) {
        if (d == null) return 0xFF3A3F4A;
        try {
            Bitmap b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 16, 16);
            d.draw(new Canvas(b));
            long rs = 0, gs = 0, bs = 0, n = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int p = b.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    rs += Color.red(p); gs += Color.green(p); bs += Color.blue(p); n++;
                }
            }
            b.recycle();
            if (n == 0) return 0xFF3A3F4A;
            return Color.rgb((int) (rs / n), (int) (gs / n), (int) (bs / n));
        } catch (RuntimeException e) {
            return 0xFF3A3F4A;
        }
    }

    static int darken(int color, float factor) {
        return Color.rgb(Math.min(255, (int) (Color.red(color) * factor)),
                Math.min(255, (int) (Color.green(color) * factor)),
                Math.min(255, (int) (Color.blue(color) * factor)));
    }
}
