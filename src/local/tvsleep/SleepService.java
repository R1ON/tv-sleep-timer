package local.tvsleep;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/** Нужен только чтобы отправить телевизор в сон: на этом ТВ нет администраторов устройства. */
public class SleepService extends AccessibilityService {
    static SleepService instance;

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    static boolean sleep() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
