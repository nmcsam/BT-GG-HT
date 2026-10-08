package app.thien.tracker;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Phát res/raw/bell_chua.wav (3 tiếng chuông chùa) theo luồng BÁO THỨC — reo cả khi tắt màn hình. */
public class BellService extends Service {
    private MediaPlayer mp;
    private PowerManager.WakeLock wl;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int min = intent != null ? intent.getIntExtra("min", 0) : 0;
        Bell.ensureChannels(this);
        Notification n = buildNotif(min);
        if (Build.VERSION.SDK_INT >= 29) startForeground(Bell.NOTIF_BELL, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(Bell.NOTIF_BELL, n);

        stopPlayer();
        try {
            PowerManager pm = getSystemService(PowerManager.class);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tc:bellsvc");
            wl.acquire(60_000L);
        } catch (Exception ignored) { }
        try {
            Vibrator v = getSystemService(Vibrator.class);
            long[] pat = {0, 600, 400, 600, 400, 600};
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pat, -1)); else v.vibrate(pat, -1);
        } catch (Exception ignored) { }
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            AssetFileDescriptor fd = getResources().openRawResourceFd(R.raw.bell_chua);
            mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            mp.setLooping(false);
            mp.setOnCompletionListener(p -> finish());
            mp.prepare();
            mp.setVolume(1f, 1f);
            mp.start();
        } catch (Exception e) {
            finish();
            return START_NOT_STICKY;
        }
        h.postDelayed(this::finish, 50_000L); // chặn chắc chắn (tiếng chuông dài 36 giây)
        return START_NOT_STICKY;
    }

    private Notification buildNotif(int min) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, Bell.CH_BELL) : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle(min > 0 ? "🔔 Đủ " + min + " phút thiền" : "🔔 Thử chuông thiền")
                .setContentText("Chạm đồng hồ trong app khi xả thiền để ghi")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(Bell.openApp(this))
                .setAutoCancel(true)
                .build();
    }

    private void finish() {
        stopPlayer();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_DETACH); else stopForeground(false);
        stopSelf();
    }

    private void stopPlayer() {
        h.removeCallbacksAndMessages(null);
        if (mp != null) {
            try { mp.stop(); } catch (Exception ignored) { }
            try { mp.release(); } catch (Exception ignored) { }
            mp = null;
        }
        if (wl != null) {
            try { if (wl.isHeld()) wl.release(); } catch (Exception ignored) { }
            wl = null;
        }
    }

    @Override public void onDestroy() { stopPlayer(); super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }
}
