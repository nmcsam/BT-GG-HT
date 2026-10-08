package app.thien.tracker;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * APK BT.GG.HT — WebView bọc app chung https://nmcsam.github.io/BT-GG-HT/
 *
 * CHUÔNG THIỀN (vì sao cần APK):
 *  Trên trình duyệt, Android có thể tắt âm thanh/đồng hồ của trang khi tắt màn hình → chuông câm.
 *  APK này giao việc reo chuông cho ĐỒNG HỒ HỆ THỐNG (AlarmClock.ACTION_SET_TIMER) → luôn reo.
 *
 * CẦU NỐI (không bao giờ vỡ khi đổi giao diện web):
 *  - addJavascriptInterface("TCAndroidBridge") có mặt trong MỌI khung, kể cả khung Thiền (thien.html)
 *    nằm bên trong trang chung. Trang Thiền tự nhận ra cầu nối và gọi TCAndroidBridge.setTimer(phút).
 *  - User-Agent có thêm " TCAndroid" để trang nhận biết sớm.
 *  - Dự phòng: vẫn bắt đường dẫn thienclock://set?min=N.
 *  KHÔNG chèn mã JavaScript từ ngoài vào trang (cách cũ đã vỡ khi gộp 2 app vào một).
 */
public class MainActivity extends Activity {

    private static final String HOME = "https://nmcsam.github.io/BT-GG-HT/";
    private static final int REQ_FILE = 7;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    /** Cầu nối gọi từ trang web (có mặt trong mọi khung). */
    public class Bridge {
        @JavascriptInterface
        public void setTimer(int minutes) {
            final int m = Math.max(1, Math.min(1440, minutes));
            runOnUiThread(() -> openClockTimer(m));
        }
        @JavascriptInterface
        public void cancelTimer() {
            // Không tự huỷ hẹn giờ của Đồng hồ (tránh đóng nhầm hẹn giờ khác của người dùng);
            // xả thiền sớm thì tắt hẹn giờ ngay trong app Đồng hồ.
        }
        @JavascriptInterface
        public String version() { return "BT.GG.HT-android-1"; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setUserAgentString(s.getUserAgentString() + " TCAndroid");

        web.addJavascriptInterface(new Bridge(), "TCAndroidBridge");

        web.setWebChromeClient(new WebChromeClient() {
            // Cho nút "Nhập dữ liệu (JSON)" mở được trình chọn file
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try {
                    startActivityForResult(p.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Không mở được trình chọn file", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUri(request.getUrl());
            }
            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUri(Uri.parse(url));
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(HOME);
        }
    }

    private boolean handleUri(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (scheme == null) return false;

        if ("thienclock".equalsIgnoreCase(scheme)) {
            if ("cancel".equalsIgnoreCase(uri.getHost())) return true; // không mở nhầm hẹn giờ
            int min = 30;
            try {
                String m = uri.getQueryParameter("min");
                if (m != null) min = Integer.parseInt(m.trim());
            } catch (Exception ignored) { }
            openClockTimer(Math.max(1, Math.min(1440, min)));
            return true;
        }

        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            String host = uri.getHost();
            if (host != null && (host.equalsIgnoreCase("nmcsam.github.io")
                    || host.endsWith("gstatic.com") || host.endsWith("googleapis.com")
                    || host.endsWith("cdnjs.cloudflare.com"))) {
                return false;
            }
            openExternally(uri);
            return true;
        }
        if ("blob".equalsIgnoreCase(scheme) || "data".equalsIgnoreCase(scheme) || "about".equalsIgnoreCase(scheme)) {
            return false;
        }
        openExternally(uri);
        return true;
    }

    private void openClockTimer(int minutes) {
        Intent i = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Thiền " + minutes + " phút")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Không tìm thấy app Đồng hồ hỗ trợ Hẹn giờ", Toast.LENGTH_LONG).show();
        }
    }

    private void openExternally(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) { }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }
}
