package pt.andrades.tv;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/** Um cartão do ecrã inicial: um app instalado, um favorito por instalar, ou um atalho (Definições). */
final class App {
    final String pkg;
    final String name;
    final String tagline;
    /** Cores do destaque grande quando o cartão está em foco. */
    final int colorTop, colorBottom;
    /** Logótipo desenhado quando o app não tem banner (por exemplo, ainda não está instalado). */
    final Logo logo;
    final Drawable banner, icon;
    /** null quando o app não está instalado. */
    final Intent launch;
    /** Favorito que não vem da Play Store: instala-se pelo computador (tools/instalar.sh). */
    final boolean sideload;
    final boolean vpn;

    /** Um logótipo em texto: fundo, cor das letras, texto e, opcionalmente, um símbolo antes dele. */
    static final class Logo {
        final int bg, fg;
        final String text;
        final String mark;
        final int markColor;
        final boolean lowercase;

        Logo(int bg, int fg, String text, String mark, int markColor, boolean lowercase) {
            this.bg = bg;
            this.fg = fg;
            this.text = text;
            this.mark = mark;
            this.markColor = markColor;
            this.lowercase = lowercase;
        }
    }

    App(String pkg, String name, String tagline, int colorTop, int colorBottom, Logo logo,
        Drawable banner, Drawable icon, Intent launch, boolean sideload, boolean vpn) {
        this.pkg = pkg;
        this.name = name;
        this.tagline = tagline;
        this.colorTop = colorTop;
        this.colorBottom = colorBottom;
        this.logo = logo;
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
