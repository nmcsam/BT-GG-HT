# APK BT.GG.HT — vì sao có, và cách dựng lại

## Vì sao cần APK (chuông thiền)
Trên trình duyệt (Chrome/PWA), Android có thể **tắt âm thanh và đồng hồ của trang khi tắt màn hình** → chuông thiền câm.
APK này tự đặt **báo thức chính xác** (`Bell.java` → `AlarmManager.setAlarmClock`) và khi đủ giờ `AlarmReceiver` bật
`BellService` phát **3 tiếng chuông chùa** `res/raw/bell_chua.wav` (luồng báo thức, như Meditation 5.19) → reo cả khi tắt màn hình.
Trong lúc thiền có thông báo "🧘 Đang thiền" đếm ngược. **KHÔNG mở app Đồng hồ hệ thống** (người dùng không muốn).
Thiền **lớn hơn 5 phút** mới ghi kết quả (thien.html → `timerTap`).

## Cầu nối — QUY TẮC KHÔNG ĐƯỢC PHÁ (để không mất chuông lần nữa)
- APK gắn `TCAndroidBridge` (addJavascriptInterface) vào **mọi khung**, kể cả `thien.html` nằm trong `index.html`.
- `thien.html` tự bật `window.TCAndroid` khi thấy `TCAndroidBridge`,
  và gọi `tcClockSet(phút)` → `TCAndroidBridge.setTimer(phút)`; xả thiền → `cancelTimer()`; nút Thử chuông → `testBell()`.
- `window.TCAndroid` chỉ bật khi cầu nối có `testBell` (APK ≥ 1.3). APK cũ 1.0–1.2 mở app Đồng hồ → bị coi như trình duyệt.
- **Không** chèn mã JavaScript từ ngoài vào trang (APK Meditation cũ làm vậy; gộp 2 app vào khung là vỡ → mất chuông).
- Khi sửa `thien.html`: giữ nguyên `tcClockSet`, `tcClockCancel`, đoạn nhận biết `TCAndroid` trong `<head>`,
  và các nhánh `if(window.TCAndroid)` trong `timerTap` / `timerBegin` / `timerTick`.
- Kiểm tra nhanh: app → ⚙ Cài đặt → **🔔 Chuông thiền** phải ghi **✓ 3 tiếng chuông (app)** (trong APK); bấm **Thử chuông** phải nghe 3 tiếng chuông chùa.

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

## Phát hành bản APK mới (người dùng chỉ việc bấm "Cập nhật" trong app)
1. Tăng `versionCode` / `versionName` trong `app/build.gradle`, dựng lại (như trên).
2. Chép APK vào `app/BT.GG.HT.apk` (thư mục web) và sửa `app/version.json` cho đúng `versionCode`.
3. Commit + push → trong APK cũ tự hiện thanh **🆕 Có bản app mới → Cập nhật**;
   trên Chrome Android tự hiện **🔔 Cài app BT.GG.HT**.
