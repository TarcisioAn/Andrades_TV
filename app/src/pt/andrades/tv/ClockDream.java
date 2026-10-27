package pt.andrades.tv;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/** Protetor de ecrã: um relógio grande sobre um fundo escuro, que muda de sítio a cada minuto para não marcar o ecrã. */
public final class ClockDream extends DreamService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private LinearLayout box;
    private TextView time, date;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            time.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
            date.setText(new SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault()).format(now));
            FrameLayout parent = (FrameLayout) box.getParent();
            if (parent != null && parent.getWidth() > 0) {
                float maxX = Math.max(0, parent.getWidth() - box.getWidth());
                float maxY = Math.max(0, parent.getHeight() - box.getHeight());
                box.animate().x(maxX * (0.1f + 0.8f * random.nextFloat()))
                        .y(maxY * (0.1f + 0.8f * random.nextFloat())).alpha(1f).setDuration(1500).start();
            }
            main.postDelayed(this, 60_000 - (System.currentTimeMillis() % 60_000));
        }
    };

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF10131A, 0xFF000000}));
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setAlpha(0f);
        time = Ui.text(this, "", 120, 0xF2FFFFFF);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        date = Ui.text(this, "", 24, 0x99FFFFFF);
        date.setPadding(0, (int) Ui.dp(this, 12), 0, 0);
        box.addView(time);
        box.addView(date);
        root.addView(box, new FrameLayout.LayoutParams(-2, -2));
        setContentView(root);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        main.postDelayed(tick, 300);
    }

    @Override public void onDreamingStopped() {
        main.removeCallbacks(tick);
        super.onDreamingStopped();
    }
}
