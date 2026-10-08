package app.thien.tracker;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.widget.Toast;

/**
 * Cầu nối native: nhận thienclock://set?min=180 từ web app
 * rồi mở thẳng Hẹn giờ của app Đồng hồ hệ thống (Samsung/Google/...).
 * Đây chính là "quyền năng app bên thứ 3" mà tầng web bị Android chặn.
 */
public class TimerBridgeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int minutes = 180;
        try {
            Uri u0 = getIntent().getData();
            if (u0 != null && "cancel".equalsIgnoreCase(u0.getHost())) { finish(); return; } // không mở nhầm hẹn giờ
        } catch (Exception ignored) {}
        try {
            Uri u = getIntent().getData();
            if (u != null) {
                String m = u.getQueryParameter("min");
                if (m != null) minutes = Math.max(1, Math.min(1440, Integer.parseInt(m)));
            }
        } catch (Exception ignored) {}

        // Cách 1: đặt hẹn giờ trực tiếp (hiện màn hình Hẹn giờ với số phút đã nạp sẵn)
        try {
            Intent i = new Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, "Thiền " + minutes + " phút")
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            startActivity(i);
            finish();
            return;
        } catch (Exception ignored) {}

        // Cách 2: mở danh sách hẹn giờ
        try {
            startActivity(new Intent(AlarmClock.ACTION_SHOW_TIMERS));
            finish();
            return;
        } catch (Exception ignored) {}

        // Cách 3: mở thẳng app Đồng hồ Samsung / Google
        String[] pkgs = {"com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock"};
        for (String p : pkgs) {
            try {
                Intent i = getPackageManager().getLaunchIntentForPackage(p);
                if (i != null) { startActivity(i); finish(); return; }
            } catch (Exception ignored) {}
        }
        Toast.makeText(this, "Không tìm thấy app Đồng hồ", Toast.LENGTH_SHORT).show();
        finish();
    }
}
