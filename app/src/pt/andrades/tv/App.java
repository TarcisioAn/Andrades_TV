package pt.andrades.tv;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/** Um cartão do ecrã inicial: um app instalado, um favorito por instalar, ou um atalho (Definições). */
final class App {
    final String pkg;
    final String name;
    final String tagline;
    /** Cores do fundo quando o cartão está em foco (a "prateleira" do topo, como na Apple TV). */
    final int colorTop, colorBottom;
    final Drawable banner, icon;
    /** null quando o app não está instalado. */
    final Intent launch;
    /** Favorito que não vem da Play Store: instala-se pelo computador (tools/instalar.sh). */
    final boolean sideload;
    final boolean vpn;

    App(String pkg, String name, String tagline, int colorTop, int colorBottom,
        Drawable banner, Drawable icon, Intent launch, boolean sideload, boolean vpn) {
        this.pkg = pkg;
        this.name = name;
        this.tagline = tagline;
        this.colorTop = colorTop;
        this.colorBottom = colorBottom;
        this.banner = banner;
        this.icon = icon;
        this.launch = launch;
        this.sideload = sideload;
        this.vpn = vpn;
    }

    boolean installed() {
        return launch != null;
    }
}
