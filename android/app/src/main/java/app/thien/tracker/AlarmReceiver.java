package app.thien.tracker;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

/** Đủ giờ thiền → bật BellService phát 3 tiếng chuông. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        int min = intent.getIntExtra("min", 0);
        try { c.getSystemService(NotificationManager.class).cancel(Bell.NOTIF_TIMER); } catch (Exception ignored) { }
        Bell.log(c, "alarm");
        Intent s = new Intent(c, BellService.class).putExtra("min", min);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
        } catch (Exception e) {
            // Hệ thống không cho bật dịch vụ → phát chuông ngay tại đây (dự phòng)
            Bell.log(c, "svc-blocked:" + e.getClass().getSimpleName());
            playDirect(c);
        }
    }

    private void playDirect(Context c) {
        final PendingResult pr = goAsync();
        PowerManager.WakeLock wl = null;
        try {
            wl = c.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tc:bell");
            wl.acquire(45_000L);
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            AssetFileDescriptor fd = c.getResources().openRawResourceFd(R.raw.bell_chua);
            mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            mp.prepare();
            mp.start();
            final PowerManager.WakeLock w = wl;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { mp.release(); } catch (Exception ignored) { }
                try { if (w.isHeld()) w.release(); } catch (Exception ignored) { }
                pr.finish();
            }, 38_000L);
        } catch (Exception e) {
            Bell.log(c, "direct-err:" + e);
            try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception ignored) { }
            pr.finish();
        }
    }
}
