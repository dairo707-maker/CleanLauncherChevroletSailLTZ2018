package com.simple.carlauncher;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Notification access: lets the launcher read active media sessions (now playing), Waze's guidance
 * notification (NavInfo) and incoming messages (MessageReader).
 */
public class MediaListener extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        try {
            for (StatusBarNotification sbn : getActiveNotifications()) {
                if (NavInfo.WAZE.equals(sbn.getPackageName())) NavInfo.onPosted(this, sbn);
            }
        } catch (RuntimeException ignored) { }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (NavInfo.WAZE.equals(sbn.getPackageName())) NavInfo.onPosted(this, sbn);
        else MessageReader.onPosted(this, sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && NavInfo.WAZE.equals(sbn.getPackageName())) NavInfo.clear();
    }
}
