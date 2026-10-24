#!/bin/bash
# Debloat TCL Google TV (G03_4K_GB / BeyondTV4 / Realtek rtd288o) — เช่น 55C635, 60C635
# ใช้กับ divlab Launcher (ไม่ปิด launcherx/setupwraith เพราะ divlab ใช้ Home เดิมเป็นทางสำรอง)
# ปิดแบบ disable-user = กู้คืนได้:  bash debloat-tcl-g03.sh --restore
# รายการอ้างอิงจาก CleanYourTV (MIT, © 2026 Gathin) examples/tcl-g03-google-tv — ทดสอบบนเครื่องจริงแล้ว
# ใช้:  TV=192.168.1.xx:5555 bash debloat-tcl-g03.sh
TV=${TV:-192.168.1.78:5555}
A="adb -s $TV shell"

SAFE=(
  # 1. telemetry
  com.tcl.bi com.tcl.logkit com.tcl.xian.StartandroidService com.google.android.feedback
  # 2. โฆษณา / แถวแนะนำ / ติดตั้งแอปเอง
  com.tcl.dashboard com.tcl.partnercustomizer com.tcl.messagebox com.tcl.overseasappshow com.tcl.esticker
  com.tcl.tv.tclhome_passive com.tcl.waterfall.overseas android.autoinstalls.config.google.gtvpai
  com.google.android.partnersetup com.google.android.onetimeinitializer
  # 3. บริการเบื้องหลัง / แอปติดเครื่อง
  com.tcl.guard com.tcl.usercenter com.tcl.ocean.instructions com.tcl.ocean.gamecenter com.tcl.notereminder
  com.tcl.android.webview org.chromium.webview_shell com.google.android.syncadapters.calendar
  com.google.android.play.games com.tcl.gallery com.tcl.channelplus
  com.android.messaging com.android.camera2 com.android.printspooler
)
OPT_NAMES=("เบราว์เซอร์ TCL (BrowseHere)" "Google Play Movies" "YouTube Music" "เล่นไฟล์จาก USB" "Game bar (โหมดเกม/VRR)"
  "ค้นหาด้วยเสียง / Google Assistant (ปุ่มไมค์)" "Chromecast (แคสต์จากมือถือ)" "Miracast + MagiConnect (สะท้อนจอมือถือ)")
OPT_PKGS=("com.tcl.browser" "com.google.android.videos" "com.google.android.youtube.tvmusic" "com.tcl.ui_mediaCenter" "com.tcl.gamebar"
  "com.google.android.katniss com.google.android.tv.assistant com.tcl.assistant com.tcl.micmanager"
  "com.google.android.apps.mediashell"
  "com.tcl.miracast com.tcl.MultiScreenInteraction_TV")
# ห้ามปิด: com.tcl.tv com.tcl.tvinput com.tcl.suspension com.tcl.settings com.tcl.system.server com.tcl.providers.config
#          com.tcl.initsetup com.tcl.factory.view com.tcl.tcl_bt_rcu_service com.tcl.autopair com.tcl.inputmethod.international
#          com.google.android.tv.remote.service com.google.android.apps.tv.launcherx (ปุ่ม Netflix/YouTube บนรีโมตต้องใช้)

adb connect $TV >/dev/null; adb -s $TV get-state >/dev/null || { echo "ต่อทีวีไม่ได้"; exit 1; }
ALL=("${SAFE[@]}"); for p in "${OPT_PKGS[@]}"; do ALL+=($p); done

if [ "$1" = "--restore" ]; then
  for p in "${ALL[@]}"; do $A pm enable $p >/dev/null 2>&1 && echo "เปิดคืน $p"; done
  echo "เสร็จ — รีบูตทีวี: adb -s $TV reboot"; exit 0
fi

LOG="debloat-$(date +%Y%m%d_%H%M%S).log"
off() { for p in "$@"; do
  if $A pm list packages -e | tr -d '\r' | grep -qx "package:$p"; then
    $A pm disable-user --user 0 $p >/dev/null 2>&1 && echo "ปิด  $p" | tee -a "$LOG"
  fi; done; }

echo "== ปิดกลุ่มปลอดภัย (${#SAFE[@]} แพ็กเกจ, ข้ามตัวที่ปิดอยู่แล้ว) =="
off "${SAFE[@]}"
echo; echo "== ตัวเลือก: ตอบ y เพื่อปิด, Enter เพื่อเก็บไว้ =="
for i in "${!OPT_NAMES[@]}"; do
  read -r -p "ปิด ${OPT_NAMES[$i]}? [y/N] " a
  [ "$a" = y ] && off ${OPT_PKGS[$i]}
done
echo; echo "เสร็จ — บันทึกไว้ที่ $LOG · แนะนำรีบูต: adb -s $TV reboot · กู้คืน: bash $0 --restore"
