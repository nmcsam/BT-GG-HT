package app.thien.tracker;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Chuông thiền NATIVE (như bản Meditation 5.19 đã chạy tốt):
 *  - Đặt báo thức CHÍNH XÁC của hệ thống (AlarmManager) → đủ giờ, AlarmReceiver bật BellService
 *    phát res/raw/bell_chua.wav (3 tiếng chuông chùa) — vẫn reo khi tắt màn hình / khoá máy.
 *  - Trong lúc thiền: thông báo "Đang thiền" đếm ngược trên màn hình khoá.
 *  - KHÔNG mở app Đồng hồ hệ thống.
 */
final class Bell {
    static final String CH_TIMER = "med_timer2";
    static final String CH_BELL = "med_bell";
    static final int NOTIF_TIMER = 1002;
    static final int NOTIF_BELL = 1001;
    private static final int REQ_ALARM = 7001;

    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel t = new NotificationChannel(CH_TIMER, "Đang thiền (đếm giờ)", NotificationManager.IMPORTANCE_LOW);
        t.setDescription("Hiện đồng hồ đếm khi đang thiền (trên màn hình khóa)");
        t.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        t.setSound(null, null);
        nm.createNotificationChannel(t);
        NotificationChannel b = new NotificationChannel(CH_BELL, "Chuông thiền", NotificationManager.IMPORTANCE_HIGH);
        b.setDescription("Báo khi đủ giờ thiền");
        b.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        b.setSound(null, null); // âm thanh do BellService phát (3 tiếng chuông)
        nm.createNotificationChannel(b);
    }

    private static PendingIntent alarmIntent(Context c, int min) {
        Intent i = new Intent(c, AlarmReceiver.class).putExtra("min", min);
        return PendingIntent.getBroadcast(c, REQ_ALARM, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Đặt chuông sau `min` phút tính từ startAt. */
    static void schedule(Context c, int min, long startAt) {
        long at = startAt + min * 60_000L;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = alarmIntent(c, min);
        am.cancel(pi);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) {
                // setAlarmClock: hệ thống coi như báo thức → đánh thức máy đúng giờ kể cả khi ngủ sâu
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, openApp(c)), pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
        log(c, "set");
        showTimerNotif(c, min, at);
    }

    static void cancel(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.cancel(alarmIntent(c, 0));
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.cancel(NOTIF_TIMER);
        nm.cancel(NOTIF_BELL);
        c.stopService(new Intent(c, BellService.class));
    }

    static void ringNow(Context c) {
        Intent s = new Intent(c, BellService.class).putExtra("min", 0);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
    }

    /** Ghi lại sự kiện chuông gần nhất (để Cài đặt hiện chẩn đoán). */
    static void log(Context c, String what) {
        try {
            String t = new java.text.SimpleDateFormat("HH:mm:ss dd/MM", java.util.Locale.US).format(new java.util.Date());
            c.getSharedPreferences("bell", 0).edit().putString(what.startsWith("err") || what.contains("-err") || what.startsWith("svc") ? "lastErr" : "last_" + what, t + (what.contains(":") ? " " + what : "")).apply();
        } catch (Exception ignored) { }
    }

    /** Chẩn đoán cho trang Cài đặt: quyền, âm lượng, lần reo gần nhất. */
    static String info(Context c) {
        StringBuilder b = new StringBuilder();
        try {
            AlarmManager am = c.getSystemService(AlarmManager.class);
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            boolean notif = c.getSystemService(NotificationManager.class).areNotificationsEnabled();
            android.media.AudioManager au = c.getSystemService(android.media.AudioManager.class);
            int v = au.getStreamVolume(android.media.AudioManager.STREAM_ALARM), mx = au.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM);
            android.content.SharedPreferences p = c.getSharedPreferences("bell", 0);
            b.append("{\"exact\":").append(exact).append(",\"notif\":").append(notif)
             .append(",\"vol\":").append(v).append(",\"max\":").append(mx)
             .append(",\"set\":\"").append(p.getString("last_set", "")).append('"')
             .append(",\"alarm\":\"").append(p.getString("last_alarm", "")).append('"')
             .append(",\"ring\":\"").append(p.getString("last_ring", "")).append('"')
             .append(",\"err\":\"").append(p.getString("lastErr", "").replace("\"", "'").replace("\\", "/")).append("\"}");
        } catch (Exception e) { return "{}"; }
        return b.toString();
    }

    static void showTimerNotif(Context c, int min, long at) {
        ensureChannels(c);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CH_TIMER) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle("🧘 Đang thiền " + min + " phút")
                .setContentText("Đủ giờ sẽ đổ 3 tiếng chuông")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(at)
                .setUsesChronometer(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(openApp(c));
        if (Build.VERSION.SDK_INT >= 24) b.setChronometerCountDown(true);
        try { c.getSystemService(NotificationManager.class).notify(NOTIF_TIMER, b.build()); } catch (Exception ignored) { }
    }
}
