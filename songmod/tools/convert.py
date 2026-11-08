#!/usr/bin/env python3
"""変換の入口: mp4 / mp3 / wav / mid などを、songmod 用の音符 JSON にして、Minecraft の songs フォルダへ入れる。

使い方(1 曲ずつ):
    python3 convert.py 曲.mp4                         ファイルが 1 つ
    python3 convert.py 鍵盤の動画.mp4 元の動画.mp4      同じ曲の動画が 2 つ

- ファイルが 1 つ: ピアノロール動画は、鍵盤の光り方から読む(いちばん正確)。鍵盤が見つからない動画や、mp3 などの音声は、
  音声から推定する。mid(MIDI)は、そのまま読む(ドラムも)。
- ファイルが 2 つ(同じ曲): 鍵盤がある動画を土台にして、鍵盤がない動画(元の動画)の音を聞いて、時刻やタイミングを
  細かく合わせ、ドラムも足す。どちらに鍵盤があるかは、自動で見分ける。結果は、元の動画のほうの名前になる。
- 3 つ以上や、フォルダは受け付けない(一度に複数の曲は、変換しない)。
できた JSON は、元のファイルと同じフォルダにも残り、Minecraft の songs フォルダへコピーされる(--no-install でコピーしない)。
このほかの指定(--max-poly など)は、そのまま変換ツールへ渡される。
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
VIDEO = {".mp4", ".mkv", ".webm", ".mov", ".avi", ".m4v"}
AUDIO = {".mp3", ".wav", ".flac", ".ogg", ".m4a", ".aac", ".opus"}
MIDI = {".mid", ".midi"}


def run(tool, files, extra):
    cmd = [sys.executable, os.path.join(HERE, tool)] + files + extra
    return subprocess.run(cmd).returncode


BOOL_FLAGS = {"--install", "--no-install", "--drums", "--no-drums", "--snare", "--spread", "--no-audio-check", "--one-row"}


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


def check_requirements():
    """足りないものがあれば、何をすればよいかを教えて終わる"""
    import shutil
    if shutil.which("ffmpeg") is None:
        sys.exit("ffmpeg が見つかりません。入れてから、もう一度やってください(Windows: winget install ffmpeg)。")
    try:
        import numpy  # noqa: F401
    except ImportError:
        sys.exit("numpy が入っていません。tools フォルダの install_requirements.bat を実行してください"
                 "(手動なら: python -m pip install --only-binary=:all: numpy)。")


def main():
    check_requirements()
    paths, extra = split_args(sys.argv[1:])
    install = "--no-install" not in extra
    extra = [a for a in extra if a != "--no-install"]
    if install:
        extra.append("--install")
    if not paths:
        print(__doc__)
        sys.exit(1)

    for p in paths:
        if os.path.isdir(p):
            sys.exit(f"フォルダは指定できません({p})。一度に変換できるのは、1 曲(ファイル 1 つ)か、同じ曲の 2 つ(鍵盤の動画と元の動画)です。")
        if not os.path.isfile(p):
            sys.exit(f"ファイルが見つかりません: {p}")
    if len(paths) > 2:
        sys.exit(f"ファイルが {len(paths)} 個あります。一度に変換できるのは、1 曲(ファイル 1 つ)か、同じ曲の 2 つ"
                 "(鍵盤の動画と元の動画)までです。曲ごとに分けて、やり直してください。")

    if len(paths) == 2:
        sys.exit(run_pair(paths, extra))
    f = paths[0]
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
    sys.exit(rc)


def has_keyboard(path):
    """動画に、鍵盤(ピアノロールの鍵盤)が映っているか。動画でなければ False"""
    if os.path.splitext(path)[1].lower() not in VIDEO:
        return False
    sys.path.insert(0, HERE)
    import video_to_notes
    return video_to_notes.detect_keyboard(path, verbose=False) is not None


def run_pair(paths, extra):
    """同じ曲の 2 つのファイル: 鍵盤がある動画と、元の動画(鍵盤がない)に分けて、細かく変換する"""
    print("2 つのファイルから、鍵盤のある動画を探しています…", file=sys.stderr)
    kb = [p for p in paths if has_keyboard(p)]
    if len(kb) == 0:
        print("どちらの動画にも、鍵盤が見つかりませんでした。鍵盤のある動画(ピアノロール)と、元の動画を 1 つずつ指定してください。\n"
              "(鍵盤がない動画だけの変換は、1 つずつ指定します)", file=sys.stderr)
        return 1
    if len(kb) == 2:
        print("どちらの動画にも、鍵盤があります。鍵盤のある動画と、鍵盤のない元の動画を 1 つずつ指定してください。\n"
              "(別々の曲なら、1 つずつ変換してください)", file=sys.stderr)
        return 1
    orig = paths[0] if kb[0] == paths[1] else paths[1]
    print(f"  鍵盤のある動画: {os.path.basename(kb[0])}\n  元の動画:       {os.path.basename(orig)}", file=sys.stderr)
    return run("pair_to_notes.py", [kb[0], orig], extra)


if __name__ == "__main__":
    main()
