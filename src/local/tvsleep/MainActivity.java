package local.tvsleep;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int[] MINUTES = {5, 10, 20, 30, 40, 50, 60, 90, 120, 150, 180};
    private static final String[] LABELS = {"5 мин", "10 мин", "20 мин", "30 мин", "40 мин", "50 мин", "1 ч", "1,5 ч", "2 ч", "2,5 ч", "3 ч"};
    // Ползунок шагами по 5%: от 5% до 100%
    private static final int STEP = 5;

    // Тёмная тема для ночи: приглушённый текст, фокус — тёплый янтарный
    private static final int BG = Color.rgb(6, 6, 10);
    private static final int BUTTON_BG = Color.rgb(24, 24, 30);
    private static final int TEXT = Color.rgb(140, 144, 156);
    private static final int ACCENT = Color.rgb(242, 170, 60);
    private static final int ACCENT_BG = Color.rgb(60, 40, 10);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView timerText;
    private TextView levelText;
    private TextView warningText;
    private SeekBar seek;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateTimerText();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(64), dp(40), dp(64), dp(40));

        timerText = text(28);
        root.addView(timerText);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        for (int i = 0; i < MINUTES.length; i++) {
            int minutes = MINUTES[i];
            addCell(grid, button(LABELS[i], v -> DimService.send(this, DimService.ACTION_TIMER, minutes)));
        }
        addCell(grid, button("Отменить таймер", v -> DimService.send(this, DimService.ACTION_CANCEL, 0)));
        root.addView(grid, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        levelText = text(24);
        levelText.setPadding(0, dp(32), 0, dp(8));
        root.addView(levelText);

        seek = new SeekBar(this);
        seek.setMax(100 / STEP - 1);
        seek.setKeyProgressIncrement(1);
        seek.setPadding(dp(24), 0, dp(24), 0);
        seek.setBackground(focusFrame(Color.TRANSPARENT, Color.TRANSPARENT));
        seek.setProgressTintList(ColorStateList.valueOf(ACCENT));
        seek.setThumbTintList(ColorStateList.valueOf(ACCENT));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(60, 60, 70)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int level = (progress + 1) * STEP;
                levelText.setText("Яркость: " + level + "%");
                if (fromUser) DimService.send(MainActivity.this, DimService.ACTION_LEVEL, level);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        seekLp.setMargins(dp(6), 0, dp(6), dp(12));
        root.addView(seek, seekLp);

        Button restore = button("Вернуть яркость", v -> {
            DimService.send(this, DimService.ACTION_RESTORE, 0);
            setSeekLevel(100);
        });
        LinearLayout.LayoutParams restoreLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        restoreLp.setMargins(dp(6), 0, dp(6), 0);
        root.addView(restore, restoreLp);

        warningText = text(18);
        warningText.setTextColor(Color.rgb(255, 170, 120));
        root.addView(warningText);

        setContentView(root);
        grid.getChildAt(0).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setSeekLevel(DimService.level(this));
        warningText.setText(missingPermissions());
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private void setSeekLevel(int level) {
        seek.setProgress(level / STEP - 1);
        levelText.setText("Яркость: " + level + "%");
    }

    private void updateTimerText() {
        long left = DimService.timerEnd(this) - System.currentTimeMillis();
        if (left <= 0) {
            timerText.setText("Таймер не запущен");
            return;
        }
        long sec = left / 1000;
        timerText.setText(String.format("Выключение через %d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60));
    }

    private String missingPermissions() {
        StringBuilder sb = new StringBuilder();
        if (!Settings.System.canWrite(this)) sb.append("Нет права менять яркость. ");
        if (!Settings.canDrawOverlays(this)) sb.append("Нет права на затемнение поверх экрана. ");
        String services = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (services == null || !services.contains(getPackageName() + "/")) sb.append("Нет права выключать телевизор. ");
        return sb.toString();
    }

    private void addCell(GridLayout grid, Button b) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.height = dp(56);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        grid.addView(b, lp);
    }

    /** Фон с толстой янтарной рамкой в фокусе. */
    private StateListDrawable focusFrame(int normalColor, int focusedColor) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(normalColor);
        normal.setCornerRadius(dp(10));
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(focusedColor);
        focused.setCornerRadius(dp(10));
        focused.setStroke(dp(4), ACCENT);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    private TextView text(int sp) {
        TextView tv = new TextView(this);
        tv.setTextColor(TEXT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return tv;
    }

    private Button button(String label, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        b.setGravity(Gravity.CENTER);
        b.setBackground(focusFrame(BUTTON_BG, ACCENT_BG));
        b.setStateListAnimator(null);
        b.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{ACCENT, TEXT}));
        b.setOnClickListener(onClick);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
