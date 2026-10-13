#!/bin/bash
# Push Arsip Senja Android ke GitHub + build APK via Actions + unduh artifact.
# Butuh: export GH_TOKEN=<token user> sebelum jalan.
set -euo pipefail
cd ~/workspace/arsip-senja-android

echo "$GH_TOKEN" | gh auth login --with-token -h github.com 2>/dev/null || true
# Auth via GH_TOKEN env sudah cukup; pastikan valid:
gh auth status -h github.com >/dev/null || { echo "GH auth gagal"; exit 1; }

if [ ! -d .git ]; then git init -b main; fi
git add -A
git -c user.name="arsip-senja" -c user.email="arsip-senja@local" commit -m "Arsip Senja Android (Kotlin + TDLib + Compose)" || true

if ! gh repo view arsip-senja-android >/dev/null 2>&1; then
  gh repo create arsip-senja-android --private --source=. --push
else
  git remote add origin "$(gh repo view arsip-senja-android --json url -q .url).git" 2>/dev/null || true
  git push -u origin main
fi

echo "--- menunggu workflow selesai ---"
RUN_ID=$(gh run list --workflow=build-apk.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run watch "$RUN_ID" --exit-status || true

echo "--- unduh APK ---"
mkdir -p ~/workspace/arsip-senja-android/apk
gh run download "$RUN_ID" -n arsip-senja-debug -D ~/workspace/arsip-senja-android/apk
ls -la ~/workspace/arsip-senja-android/apk/
