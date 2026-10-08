package app.thien.tracker;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Đủ giờ thiền → bật BellService phát 3 tiếng chuông. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        int min = intent.getIntExtra("min", 0);
        try { c.getSystemService(NotificationManager.class).cancel(Bell.NOTIF_TIMER); } catch (Exception ignored) { }
        Intent s = new Intent(c, BellService.class).putExtra("min", min);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
        } catch (Exception e) {
            try { c.startService(s); } catch (Exception ignored) { }
        }
    }
}
