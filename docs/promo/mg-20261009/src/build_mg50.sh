#!/usr/bin/env bash
set -o pipefail
T="$LOCALAPPDATA/Temp"
PY="C:/Users/skps9/AppData/Local/Python/pythoncore-3.14-64/python.exe"
FF="C:/Users/skps9/AppData/Local/Microsoft/WinGet/Packages/Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe/ffmpeg-9.0-full_build/bin/ffmpeg.exe"
OUT="C:/Users/skps9/Documents/Code_Project/super_minecraft_AI_player/docs/promo/mg-20261009"
MUS="$OUT/../narration-20261009/mus_mozart_figaro_cc0.ogg"
mkdir -p "$OUT"

echo "=== [1/3] composite v3 (50s) ==="
rm -rf "$T/mg2_out"; mkdir -p "$T/mg2_out"
"$PY" "$T/mg_promo_v3.py"
N=$(ls "$T/mg2_out"/g_*.png 2>/dev/null | wc -l); echo "composited=$N"
if [ "$N" -ne 1509 ]; then echo "ABORT: frames=$N expected 1509"; exit 1; fi

echo "=== [2/3] encode video ==="
"$FF" -y -v error -framerate 30 -i "$T/mg2_out/g_%05d.png" \
  -c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p -movflags +faststart \
  "$OUT/promo_mg_50s_video.mp4"
echo "video_rc=$?"

echo "=== [3/3] music ==="
"$FF" -y -v error -i "$OUT/promo_mg_50s_video.mp4" -i "$MUS" \
  -map 0:v:0 -map 1:a:0 -c:v copy \
  -af "afade=t=in:d=1.2,afade=t=out:st=47.0:d=3.5,loudnorm=I=-16:TP=-1.5:LRA=11" \
  -c:a aac -b:a 192k -shortest -movflags +faststart "$OUT/promo_mg_50s.mp4"
echo "final_rc=$?"
ls -la "$OUT"; echo ALLDONE
