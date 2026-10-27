package pt.andrades.tv;

import java.util.Calendar;

/** As cores do ecrã: vidro claro de dia, vidro escuro à noite (das 20h às 7h). */
final class Theme {
    final boolean dark;
    final int bgTop, bgBottom;
    final int text, textDim;
    final int glass, glassStroke;
    final int shadow;

    private Theme(boolean dark) {
        this.dark = dark;
        if (dark) {
            bgTop = 0xFF22252D; bgBottom = 0xFF0B0C10;
            text = 0xFFF5F5F7; textDim = 0x99F5F5F7;
            glass = 0x26FFFFFF; glassStroke = 0x33FFFFFF;
            shadow = 0x66000000;
        } else {
            bgTop = 0xFFF2F4F7; bgBottom = 0xFFD5DBE3;
            text = 0xFF1D1D1F; textDim = 0x991D1D1F;
            glass = 0x8CFFFFFF; glassStroke = 0xCCFFFFFF;
            shadow = 0x33000000;
        }
    }

    static Theme now() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return new Theme(h >= 20 || h < 7);
    }

    static String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 5 && h < 12) return "Bom dia";
        if (h >= 12 && h < 20) return "Boa tarde";
        return "Boa noite";
    }
}
