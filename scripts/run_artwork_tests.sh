#!/bin/bash
set -e

# 1. UIAutomator テスト実行（screencap & screenrecord）
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.datathrottle.ArtworkGenerationTest --stacktrace --no-daemon

# 2. 静止画をエミュレータから回収
mkdir -p artworks/screenshots/en
for f in 01_permission_setup.png 02_home_off.png 03_bandwidth_picker.png 04_home_on.png 05_settings.png 06_test_screen.png; do
  adb pull "/sdcard/$f" "artworks/screenshots/en/$f" || true
  adb shell rm -f "/sdcard/$f" || true
done

# 3. 動画をエミュレータから回収
mkdir -p artworks/videos
for f in 01_toggle_switch.mp4 02_drumroll_picker.mp4 03_100k_test.mp4; do
  adb pull "/sdcard/$f" "artworks/videos/$f" || true
  adb shell rm -f "/sdcard/$f" || true
done

# 4. FFmpeg で動画パディング（先頭0.5s・末尾1.0s）& GIF 変換
for f in artworks/videos/*.mp4; do
  [ -f "$f" ] || continue
  tmp_f="/tmp/$(basename "$f")"
  ffmpeg -y -i "$f" -vf "tpad=start_duration=0.5:stop_duration=1.0:start_mode=clone:stop_mode=clone,fps=30" -c:v libx264 -pix_fmt yuv420p "$tmp_f"
  mv "$tmp_f" "$f"
  gif="${f%.mp4}.gif"
  ffmpeg -y -i "$f" -vf "fps=15,scale=360:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse" "$gif"
done
