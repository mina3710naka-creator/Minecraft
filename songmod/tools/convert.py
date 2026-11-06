#!/usr/bin/env python3
"""何でも変換: mp4 / mp3 / wav / mid などを、songmod 用の音符 JSON にして、Minecraft の songs フォルダへ入れる。

使い方(ファイルや、ファイルの入ったフォルダを、いくつでも):
    python3 convert.py 曲.mp4
    python3 convert.py 曲1.mp4 曲2.mp3 フォルダ名

動画(ピアノロールの動画)は、鍵盤の光り方から読む(いちばん正確)。鍵盤が見つからない動画は、音声から推定する。
mp3 などの音声は音声から推定し、mid(MIDI)はそのまま読む(ドラムも)。
できた JSON は、動画などと同じフォルダにも残り、Minecraft の songs フォルダへコピーされる(--no-install でコピーしない)。
このほかの指定(--max-poly など)は、そのまま変換ツールへ渡される。
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
VIDEO = {".mp4", ".mkv", ".webm", ".mov", ".avi", ".m4v"}
AUDIO = {".mp3", ".wav", ".flac", ".ogg", ".m4a", ".aac", ".opus"}
MIDI = {".mid", ".midi"}


def expand(paths):
    out = []
    for p in paths:
        if os.path.isdir(p):
            for name in sorted(os.listdir(p)):
                full = os.path.join(p, name)
                if os.path.isfile(full) and os.path.splitext(name)[1].lower() in VIDEO | AUDIO | MIDI:
                    out.append(full)
        else:
            out.append(p)
    return out


def run(tool, files, extra):
    cmd = [sys.executable, os.path.join(HERE, tool)] + files + extra
    return subprocess.run(cmd).returncode


BOOL_FLAGS = {"--install", "--no-install", "--drums", "--no-drums", "--spread", "--no-audio-check", "--one-row"}


def split_args(argv):
    """ファイルと、変換ツールへ渡すオプション(値をとるものは、値ごと)に分ける"""
    paths, extra = [], []
    i = 0
    while i < len(argv):
        a = argv[i]
        if a.startswith("--"):
            extra.append(a)
            if a not in BOOL_FLAGS and "=" not in a and i + 1 < len(argv):
                i += 1
                extra.append(argv[i])
        else:
            paths.append(a)
        i += 1
    return paths, extra


def main():
    paths, extra = split_args(sys.argv[1:])
    install = "--no-install" not in extra
    extra = [a for a in extra if a != "--no-install"]
    if install:
        extra.append("--install")
    if not paths:
        print(__doc__)
        sys.exit(1)

    failed = 0
    for f in expand(paths):
        ext = os.path.splitext(f)[1].lower()
        if ext in MIDI or ext in AUDIO:
            rc = run("audio_to_notes.py", [f], extra)
        elif ext in VIDEO:
            video_extra = [a for a in extra if a not in ("--drums", "--no-drums")]
            rc = run("video_to_notes.py", [f, "--require-keyboard"], video_extra)
            if rc == 3:
                print("  → 鍵盤が見つからないので、音声から推定します", file=sys.stderr)
                rc = run("audio_to_notes.py", [f], extra)
        else:
            print(f"== {f}\n  対応していない形式です(mp4 / mp3 / wav / mid など)", file=sys.stderr)
            rc = 1
        failed += rc != 0
    if failed:
        print(f"\n{failed} 個の変換に失敗しました", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
