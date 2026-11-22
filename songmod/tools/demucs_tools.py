"""Demucs を使った音源分離と、パート別の音符・ドラム抽出処理。

入力音源をドラム(drums)、ベース(bass)、ボーカル(vocals)、その他(other)に分離し、
それぞれのパートに特化した解析を行うことで、誤検出の少ない高精度な音符データを生成する。
"""
import os
import shutil
import subprocess
import sys
import tempfile

import songtools

LOW, HIGH = songtools.LOW, songtools.HIGH


def is_demucs_available():
    """demucs コマンドまたは Python パッケージが利用可能か判定する"""
    if shutil.which("demucs") is not None:
        return True
    try:
        import demucs  # noqa: F401
        return True
    except ImportError:
        return False


def separate_audio(input_wav, output_dir):
    """Demucs を使って wav を 4 パート(drums, bass, vocals, other)に分離する。
    返り値: dict {"drums": path, "bass": path, "vocals": path, "other": path}
    """
    cmd = [sys.executable, "-m", "demucs", "-n", "htdemucs", "--two-stems=none", "-o", output_dir, input_wav]
    if shutil.which("demucs") is not None:
        cmd = ["demucs", "-n", "htdemucs", "-o", output_dir, input_wav]

    print("  [Demucs] 音源を分離しています(ドラム・ベース・ボーカル・伴奏)...", file=sys.stderr)
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"Demucs の実行に失敗しました:\n{res.stderr}")

    stem = os.path.splitext(os.path.basename(input_wav))[0]
    track_dir = os.path.join(output_dir, "htdemucs", stem)
    if not os.path.isdir(track_dir):
        # フォルダ構成の探索
        for root, dirs, files in os.walk(output_dir):
            if "drums.wav" in files:
                track_dir = root
                break

    parts = {}
    for name in ("drums", "bass", "vocals", "other"):
        p = os.path.join(track_dir, f"{name}.wav")
        if os.path.isfile(p):
            parts[name] = p
        else:
            print(f"  警告: {name}.wav が見つかりませんでした", file=sys.stderr)
    return parts


def events_from_separated_tracks(tracks, args, predict_func):
    """分離されたトラックから、パート別に音符を抽出して統合する"""
    events = []

    # 1. ドラムトラックの解析 (他の楽器の打鍵音による誤検出が起きない)
    if "drums" in tracks:
        print("  [Demucs] ドラムパートから打楽器を抽出中...", file=sys.stderr)
        drum_wav = tracks["drums"]
        for t, code, amp in songtools.detect_drums(drum_wav):
            events.append((t, t + 0.05, code, amp, songtools.drum_velocity(amp)))

    # 2. ベースパートの解析 (低音 BASS 領域に特化)
    if "bass" in tracks:
        print("  [Demucs] ベースパートから低音ラインを抽出中...", file=sys.stderr)
        bass_wav = tracks["bass"]
        bass_ev = predict_func(bass_wav, args)
        levels = songtools.levels_from_audio(bass_wav, [(s, p) for s, _, p, _ in bass_ev])
        for (s, e, p, a), v in zip(bass_ev, levels):
            # 低音〜中低音域をベースとして優先
            if p <= 60:
                events.append((s, e, p, a, v))

    # 3. ボーカルパートの解析 (主旋律)
    if "vocals" in tracks:
        print("  [Demucs] ボーカルパートからメロディを抽出中...", file=sys.stderr)
        voc_wav = tracks["vocals"]
        voc_ev = predict_func(voc_wav, args)
        levels = songtools.levels_from_audio(voc_wav, [(s, p) for s, _, p, _ in voc_ev])
        for (s, e, p, a), v in zip(voc_ev, levels):
            events.append((s, e, p, a * 1.1, min(100, int(v * 1.1))))  # 主旋律を少し強調

    # 4. その他(伴奏/コードパート)の解析
    if "other" in tracks:
        print("  [Demucs] 伴奏パートから和音・伴奏を抽出中...", file=sys.stderr)
        other_wav = tracks["other"]
        other_ev = predict_func(other_wav, args)
        levels = songtools.levels_from_audio(other_wav, [(s, p) for s, _, p, _ in other_ev])
        for (s, e, p, a), v in zip(other_ev, levels):
            events.append((s, e, p, a, v))

    return events
