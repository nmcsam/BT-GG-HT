package app.thien.tracker;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
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
 *  Trên trình duyệt, Android có thể tắt âm thanh của trang khi tắt màn hình → chuông câm.
 *  APK tự đặt báo thức chính xác (Bell/AlarmReceiver/BellService) và phát 3 tiếng chuông chùa
 *  (res/raw/bell_chua.wav, như Meditation 5.19) — reo cả khi tắt màn hình. KHÔNG mở app Đồng hồ.
 *
 * CẦU NỐI (không bao giờ vỡ khi đổi giao diện web):
 *  - addJavascriptInterface("TCAndroidBridge") có mặt trong MỌI khung, kể cả khung Thiền (thien.html).
 *    Trang Thiền gọi TCAndroidBridge.setTimer(phút) / cancelTimer() / testBell().
 *  - User-Agent có thêm " TCAndroid" để trang nhận biết sớm.
 *  - Dự phòng: vẫn bắt đường dẫn thienclock://set?min=N và thienclock://cancel.
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
            runOnUiThread(() -> setBell(m));
        }
        @JavascriptInterface
        public void cancelTimer() { Bell.cancel(MainActivity.this); }
        /** Thử chuông: đổ 3 tiếng chuông ngay. */
        @JavascriptInterface
        public void testBell() { runOnUiThread(() -> Bell.ringNow(MainActivity.this)); }
        /** Chẩn đoán chuông (JSON) cho trang Cài đặt. */
        @JavascriptInterface
        public String bellInfo() { return Bell.info(MainActivity.this); }
        @JavascriptInterface
        public String version() { return "BT.GG.HT-android-3"; }
        /** Số phiên bản APK — trang so với app/version.json để tự báo "Có bản app mới". */
        @JavascriptInterface
        public int versionCode() {
            try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; }
            catch (Exception e) { return 0; }
        }
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
        // Giữ cỡ chữ đúng như trên Chrome (không phóng theo cỡ chữ hệ thống → giao diện không vỡ)
        s.setTextZoom(100);
        s.setUserAgentString(s.getUserAgentString() + " TCAndroid");

        web.addJavascriptInterface(new Bridge(), "TCAndroidBridge");
        Bell.ensureChannels(this);
        // Android 13+: xin quyền thông báo (đồng hồ đếm trên màn hình khoá + báo đủ giờ)
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9);
        }

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
            if ("cancel".equalsIgnoreCase(uri.getHost())) { Bell.cancel(this); return true; }
            int min = 30;
            try {
                String m = uri.getQueryParameter("min");
                if (m != null) min = Integer.parseInt(m.trim());
            } catch (Exception ignored) { }
            setBell(Math.max(1, Math.min(1440, min)));
            return true;
        }

        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            String path = uri.getPath();
            if (path != null && path.toLowerCase().endsWith(".apk")) { // tải bản APK mới → để Chrome tải & cài
                openExternally(uri);
                return true;
            }
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

    private void setBell(int minutes) {
        Bell.schedule(this, minutes, System.currentTimeMillis());
        Toast.makeText(this, "🔔 Đã đặt chuông sau " + minutes + " phút", Toast.LENGTH_SHORT).show();
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
