#!/bin/sh
# mp4 / mp3 / mid を、音符の JSON に変換して、Minecraft の songs フォルダへ入れる(Mac / Linux 用)。
#   ./convert.sh 曲.mp4 [曲2.mp3 ...]
cd "$(dirname "$0")" || exit 1
exec python3 convert.py "$@"
