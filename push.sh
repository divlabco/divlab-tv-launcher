#!/bin/bash
# push → รอ build ของ commit นี้ → โหลด APK → ติดตั้งลงทีวี   ใช้: bash push.sh [v0.x.y]
set -e
cd "$(dirname "$0")"
REPO=divlabco/divlab-tv-launcher
TV=192.168.1.78:5555
VER=$1
git add -A
git commit -m "divlab TV Launcher ${VER:-update}" || true
git push origin main
[ -n "$VER" ] && git tag -f "$VER" && git push -f origin "$VER"
SHA=$(git rev-parse HEAD)
echo "รอ build ของ ${SHA:0:7}..."
until RUN=$(gh run list -R $REPO --commit $SHA --workflow build.yml --json databaseId -q '.[0].databaseId') && [ -n "$RUN" ]; do sleep 5; done
gh run watch -R $REPO "$RUN" --exit-status || { gh run view -R $REPO "$RUN" --log-failed | tail -80 > build-error.log; echo "build ไม่ผ่าน — ดู build-error.log"; exit 1; }
rm -f divlab-launcher.apk
gh run download -R $REPO "$RUN" -n divlab-launcher
adb connect $TV >/dev/null
if ! adb -s $TV install -r divlab-launcher.apk; then
  echo "signature ไม่ตรง → ถอนตัวเก่าแล้วติดตั้งใหม่"; adb -s $TV uninstall co.divlab.tvlauncher; adb -s $TV install divlab-launcher.apk
fi
adb -s $TV shell monkey -p co.divlab.tvlauncher 1 >/dev/null
adb -s $TV shell dumpsys package co.divlab.tvlauncher | grep versionName
