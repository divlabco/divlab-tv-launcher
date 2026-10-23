# divlab TV Launcher

Launcher สำหรับ Android TV / Google TV แบบเบา หน้าตาแนว Apple TV — by [divlab.co](https://divlab.co)

- พื้นหลังเบลอเปลี่ยนตามแอปที่เลือก, ไทล์ 16:9 ขยายเมื่อโฟกัส
- นาฬิกา + วันที่ภาษาไทย (พ.ศ.)
- ปุ่มลัด: แหล่งสัญญาณ / Wi-Fi / ตั้งค่า
- จัดเรียง / ซ่อนแอป: กดค้างปุ่ม OK (หรือปุ่ม Menu) บนแอป
- ไม่มีโฆษณา ไม่ขอ permission ใด ๆ ไม่มี background service (APK ~ 1 MB)
- Kotlin + Android Views ล้วน (ไม่ใช้ Compose/Leanback เพื่อให้เบาบนทีวี RAM น้อย)

## Build
Push ขึ้น GitHub แล้ว GitHub Actions จะ build ให้ → ดาวน์โหลดจาก **Actions → artifact `divlab-launcher`**
(push tag `v0.1.0` เพื่อสร้าง Release พร้อม APK)

หรือเปิดใน Android Studio แล้ว Build → Build APK

## ติดตั้งบนทีวี
```bash
adb connect <TV_IP>:5555
adb install -r divlab-launcher.apk
# ให้ปุ่ม Home มาที่ launcher นี้ (ปิด launcher เดิม)
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
# กลับไปใช้ของเดิม
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
```

ออกแบบสำหรับ TCL 55C635 (Android TV 11) — ยังไม่ได้ทดสอบบนเครื่องจริง

## License
MIT
