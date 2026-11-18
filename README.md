# WatchGuard

- `app/`   : aplikasi untuk **Android TV** (batas waktu per aplikasi, kunci semua aplikasi, server kontrol).
- `phone/` : aplikasi **WatchGuard Remote** untuk HP orang tua (kunci/buka kunci, kuis, batas waktu dan kuis tiap aplikasi, tambah waktu).

## Build
Upload semua isi folder ini ke GitHub (termasuk `.github`). Tab Actions > Build APK akan
menghasilkan dua artifact: `watchguard-tv-apk` dan `watchguard-remote-hp-apk`.

## Pasang
- TV: `adb install -r app-debug.apk`
- HP: pasang `phone-debug.apk` seperti APK biasa.
- 
## AKTIFKAN SERVICE
'adb shell settings put secure enabled_accessibility_services com.example.watchguard/com.example.watchguard.WatchGuardService
adb shell settings put secure accessibility_enabled 1'

## Pakai
HP dan TV harus di Wi-Fi yang sama. Buka WatchGuard Remote, isi PIN orang tua.
TV ditemukan otomatis (mDNS), jadi IP TV boleh berubah.
