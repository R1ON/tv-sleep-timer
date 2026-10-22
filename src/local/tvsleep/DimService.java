package local.tvsleep;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;

/** Держит таймер сна и затемнение, пока приложение закрыто. */
public class DimService extends Service {
    static final String ACTION_TIMER = "timer";
    static final String ACTION_CANCEL = "cancel";
    static final String ACTION_LEVEL = "level";
    static final String ACTION_RESTORE = "restore";

    static final String KEY_END = "end";
    static final String KEY_LEVEL = "level";
    static final String KEY_DIMMED = "dimmed";
    static final String KEY_ORIG_BRIGHTNESS = "orig_brightness";
    static final String KEY_ORIG_BACKLIGHT = "orig_backlight";

    // Подсветка из меню изображения Haier
    static final String BACKLIGHT = "picture_backlight";
    static final float MAX_OVERLAY_ALPHA = 0.95f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable sleepRunnable = this::sleepNow;
    private View overlay;
    private boolean screenWasOff;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                screenWasOff = true;
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction()) && screenWasOff) {
                screenWasOff = false;
                cancelTimer();
                restore(DimService.this);
                removeOverlay();
                stopIfIdle();
            }
        }
    };

    static void send(Context context, String action, int value) {
        Intent intent = new Intent(context, DimService.class).setAction(action).putExtra("v", value);
        context.startForegroundService(intent);
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("state", MODE_PRIVATE);
    }

    static int level(Context context) {
        return prefs(context).getInt(KEY_LEVEL, 100);
    }

    static long timerEnd(Context context) {
        return prefs(context).getLong(KEY_END, 0);
    }

    /** Возвращает яркость и подсветку, которые были до затемнения. */
    static void restore(Context context) {
        SharedPreferences p = prefs(context);
        if (p.getBoolean(KEY_DIMMED, false)) {
            putSystem(context, p.getInt(KEY_ORIG_BRIGHTNESS, -1));
            putBacklight(context, p.getInt(KEY_ORIG_BACKLIGHT, -1));
        }
        p.edit().putBoolean(KEY_DIMMED, false).putInt(KEY_LEVEL, 100).apply();
    }

    private static void putSystem(Context context, int value) {
        if (value < 0) return;
        try {
            Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
        } catch (SecurityException ignored) {
        }
    }

    private static void putBacklight(Context context, int value) {
        if (value < 0) return;
        try {
            Settings.Global.putInt(context.getContentResolver(), BACKLIGHT, value);
        } catch (SecurityException ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("main", "Сон и яркость", NotificationManager.IMPORTANCE_LOW));
        Notification notification = new Notification.Builder(this, "main")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(getString(R.string.app_name))
                .build();
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        int value = intent != null ? intent.getIntExtra("v", 0) : 0;

        if (ACTION_TIMER.equals(action)) {
            startTimer(value);
        } else if (ACTION_CANCEL.equals(action)) {
            cancelTimer();
        } else if (ACTION_LEVEL.equals(action)) {
            setLevel(value);
        } else if (ACTION_RESTORE.equals(action)) {
            restore(this);
            removeOverlay();
        } else {
            // Перезапуск системой: возвращаем то, что было
            int level = level(this);
            if (level < 100) setLevel(level);
            long left = timerEnd(this) - System.currentTimeMillis();
            if (timerEnd(this) > 0) {
                handler.postDelayed(sleepRunnable, Math.max(0, left));
            }
        }
        stopIfIdle();
        return START_STICKY;
    }

    private void startTimer(int minutes) {
        handler.removeCallbacks(sleepRunnable);
        long end = System.currentTimeMillis() + minutes * 60_000L;
        prefs(this).edit().putLong(KEY_END, end).apply();
        handler.postDelayed(sleepRunnable, minutes * 60_000L);
    }

    private void cancelTimer() {
        handler.removeCallbacks(sleepRunnable);
        prefs(this).edit().putLong(KEY_END, 0).apply();
    }

    private void sleepNow() {
        prefs(this).edit().putLong(KEY_END, 0).apply();
        SleepService.sleep();
        stopIfIdle();
    }

    /** Один ползунок: 100 — как было, ниже — гасим яркость, подсветку и накрываем экран чёрным. */
    private void setLevel(int level) {
        SharedPreferences p = prefs(this);
        if (level >= 100) {
            restore(this);
            removeOverlay();
            return;
        }
        if (!p.getBoolean(KEY_DIMMED, false)) {
            p.edit()
                    .putInt(KEY_ORIG_BRIGHTNESS, Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1))
                    .putInt(KEY_ORIG_BACKLIGHT, Settings.Global.getInt(getContentResolver(), BACKLIGHT, -1))
                    .putBoolean(KEY_DIMMED, true)
                    .apply();
        }
        p.edit().putInt(KEY_LEVEL, level).apply();

        int origBrightness = p.getInt(KEY_ORIG_BRIGHTNESS, -1);
        int origBacklight = p.getInt(KEY_ORIG_BACKLIGHT, -1);
        if (origBrightness >= 0) putSystem(this, Math.max(1, origBrightness * level / 100));
        if (origBacklight >= 0) putBacklight(this, origBacklight * level / 100);
        showOverlay(MAX_OVERLAY_ALPHA * (100 - level) / 100f);
    }

    private void showOverlay(float alpha) {
        if (!Settings.canDrawOverlays(this)) return;
        int color = Color.argb(Math.round(alpha * 255), 0, 0, 0);
        if (overlay != null) {
            overlay.setBackgroundColor(color);
            return;
        }
        overlay = new View(this);
        overlay.setBackgroundColor(color);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        getSystemService(WindowManager.class).addView(overlay, lp);
    }

    private void removeOverlay() {
        if (overlay == null) return;
        getSystemService(WindowManager.class).removeView(overlay);
        overlay = null;
    }

    private void stopIfIdle() {
        if (overlay == null && timerEnd(this) == 0 && !prefs(this).getBoolean(KEY_DIMMED, false)) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(sleepRunnable);
        unregisterReceiver(screenReceiver);
        removeOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
