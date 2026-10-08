# APK BT.GG.HT — vì sao có, và cách dựng lại

## Vì sao cần APK (chuông thiền)
Trên trình duyệt (Chrome/PWA), Android có thể **tắt âm thanh và đồng hồ của trang khi tắt màn hình** → chuông thiền câm.
APK này giao việc reo chuông cho **Đồng hồ hệ thống** (`AlarmClock.ACTION_SET_TIMER`) → chuông luôn reo.

## Cầu nối — QUY TẮC KHÔNG ĐƯỢC PHÁ (để không mất chuông lần nữa)
- APK gắn `TCAndroidBridge` (addJavascriptInterface) vào **mọi khung**, kể cả `thien.html` nằm trong `index.html`.
- `thien.html` tự bật `window.TCAndroid` khi thấy `TCAndroidBridge` (hoặc chữ `TCAndroid` trong User-Agent),
  và gọi `tcClockSet(phút)` → `TCAndroidBridge.setTimer(phút)`.
- **Không** chèn mã JavaScript từ ngoài vào trang (APK Meditation cũ làm vậy; gộp 2 app vào khung là vỡ → mất chuông).
- Khi sửa `thien.html`: giữ nguyên `tcClockSet`, `tcClockCancel`, đoạn nhận biết `TCAndroid` trong `<head>`,
  và các nhánh `if(window.TCAndroid)` trong `timerTap` / `timerBegin` / `timerTick`.
- Kiểm tra nhanh: app → ⚙ Cài đặt → **🔔 Chuông thiền** phải ghi **✓ Đồng hồ hệ thống** (trong APK).

## Cài đè lên APK Meditation cũ (giữ dữ liệu)
- `applicationId` giữ nguyên `app.thien.tracker`; `versionCode` phải **lớn hơn** bản đang cài (bản Meditation 5.19 = 120).
- Ký bằng **cùng khóa** với bản đang cài: khóa debug của máy này `C:\Users\COMPUTER\.android\debug.keystore`
  (SHA-256 `79:22:C4:B7:…:A3:13:F9`). Khác khóa → Android không cho cài đè.

## Dựng lại
OneDrive khoá file khi đang dựng → **chép ra ngoài OneDrive** rồi dựng:
```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
xcopy /E /I android %TEMP%\btgght-apk
cd %TEMP%\btgght-apk
gradlew.bat assembleDebug
```
APK: `app\build\outputs\apk\debug\app-debug.apk`. Tăng `versionCode` trong `app/build.gradle` mỗi lần phát hành.
Thay đổi giao diện web (index.html / bothi.html / thien.html) **không cần** dựng lại APK — APK luôn mở bản web mới nhất.
