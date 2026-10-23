#!/bin/bash
# สร้าง repo + push + สั่ง build + รอ + โหลด APK + ติดตั้งลงทีวี
set -e
cd "$(dirname "$0")"
REPO=divlab-tv-launcher
TV=192.168.1.78:5555
[ -d .git ] || git init -b main
git add -A
git commit -m "divlab TV Launcher v0.1.0" || true
gh repo view "$REPO" >/dev/null 2>&1 || gh repo create "$REPO" --public --source=. --remote=origin
git push -u origin main
git tag -f v0.1.0 && git push -f origin v0.1.0
echo "รอ GitHub Actions build..."; sleep 10
RUN=$(gh run list --workflow build.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run watch "$RUN" --exit-status || { gh run view "$RUN" --log-failed | tail -60 > build-error.log; echo "build ไม่ผ่าน — ดู build-error.log"; exit 1; }
rm -f divlab-launcher.apk
gh run download "$RUN" -n divlab-launcher
adb connect $TV && adb -s $TV install -r divlab-launcher.apk && adb -s $TV shell monkey -p co.divlab.tvlauncher 1 >/dev/null
echo "ติดตั้งและเปิดบนทีวีแล้ว"
