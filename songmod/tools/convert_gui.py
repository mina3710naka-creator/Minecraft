#!/usr/bin/env python3
"""Song Builder - グラフィカル曲変換ツール

Python 標準の Tkinter を使用しているため、追加ライブラリなしで動作します。
音声や動画を選択し、オプションを選んで「変換開始」を押すだけで
Minecraft の songs/ フォルダへ音符 JSON が配置されます。
"""
import os
import subprocess
import sys
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, ttk

HERE = os.path.dirname(os.path.abspath(__file__))


def get_minecraft_songs_dir():
    appdata = os.environ.get("APPDATA")
    if appdata:
        p = os.path.join(appdata, ".minecraft", "songs")
        if os.path.exists(p):
            return p
    return os.path.expanduser("~/.minecraft/songs")


class ConverterApp:

    def __init__(self, root):
        self.root = root
        self.root.title("Song Builder - 曲変換ツール")
        self.root.geometry("640x520")
        self.root.minsize(560, 420)

        self.selected_files = []

        self.setup_ui()

    def setup_ui(self):
        pad = {"padx": 10, "pady": 6}

        # 1. ファイル選択フレーム
        file_frame = ttk.LabelFrame(self.root, text=" 変換するファイル (mp3 / mp4 / mid など) ")
        file_frame.pack(fill="x", **pad)

        self.file_entry = ttk.Entry(file_frame, width=50)
        self.file_entry.pack(side="left", fill="x", expand=True, padx=8, pady=8)

        btn_browse = ttk.Button(file_frame, text="ファイル参照...", command=self.browse_files)
        btn_browse.pack(side="right", padx=8, pady=8)

        # 2. オプションフレーム
        opt_frame = ttk.LabelFrame(self.root, text=" 変換オプション ")
        opt_frame.pack(fill="x", **pad)

        self.var_install = tk.BooleanVar(value=True)
        chk_install = ttk.Checkbutton(opt_frame, text="Minecraftの songs/ フォルダへ自動で保存する", variable=self.var_install)
        chk_install.grid(row=0, column=0, columnspan=2, sticky="w", padx=8, pady=4)

        self.var_separate = tk.BooleanVar(value=False)
        chk_separate = ttk.Checkbutton(opt_frame, text="AI音源分離を使う (Demucs: ボーカル・ドラム・ベースを分離して高精度解析)", variable=self.var_separate)
        chk_separate.grid(row=1, column=0, columnspan=2, sticky="w", padx=8, pady=4)

        self.var_drums = tk.BooleanVar(value=False)
        chk_drums = ttk.Checkbutton(opt_frame, text="打楽器(バスドラム・スネア・ハイハット)も検出する", variable=self.var_drums)
        chk_drums.grid(row=2, column=0, columnspan=2, sticky="w", padx=8, pady=4)

        # 速度・移調
        lbl_speed = ttk.Label(opt_frame, text="再生速度:")
        lbl_speed.grid(row=3, column=0, sticky="e", padx=4, pady=4)
        self.entry_speed = ttk.Entry(opt_frame, width=8)
        self.entry_speed.insert(0, "1.0")
        self.entry_speed.grid(row=3, column=1, sticky="w", padx=4, pady=4)

        lbl_trans = ttk.Label(opt_frame, text="移調 (半音):")
        lbl_trans.grid(row=4, column=0, sticky="e", padx=4, pady=4)
        self.entry_trans = ttk.Entry(opt_frame, width=8)
        self.entry_trans.insert(0, "0")
        self.entry_trans.grid(row=4, column=1, sticky="w", padx=4, pady=4)

        # 3. アクションボタンフレーム
        act_frame = ttk.Frame(self.root)
        act_frame.pack(fill="x", **pad)

        self.btn_run = ttk.Button(act_frame, text="⚡ 変換開始", command=self.start_convert)
        self.btn_run.pack(side="left", padx=4, pady=4, ipadx=16, ipady=4)

        btn_open_songs = ttk.Button(act_frame, text="📂 songs フォルダを開く", command=self.open_songs_folder)
        btn_open_songs.pack(side="right", padx=4, pady=4)

        # 4. ログ出力エリア
        log_frame = ttk.LabelFrame(self.root, text=" ログ・進捗 ")
        log_frame.pack(fill="both", expand=True, **pad)

        self.txt_log = tk.Text(log_frame, wrap="word", height=10, bg="#f8f9fa", font=("Consolas", 9))
        self.txt_log.pack(side="left", fill="both", expand=True, padx=4, pady=4)

        scrollbar = ttk.Scrollbar(log_frame, command=self.txt_log.yview)
        scrollbar.pack(side="right", fill="y")
        self.txt_log.config(yscrollcommand=scrollbar.set)

    def log(self, text):
        self.txt_log.insert("end", text + "\n")
        self.txt_log.see("end")

    def browse_files(self):
        files = filedialog.askopenfilenames(
            title="変換するファイルを選択 (1曲または鍵盤動画+原曲のペア)",
            filetypes=[("音楽・動画・MIDI", "*.mp3 *.mp4 *.wav *.mid *.midi *.m4a *.flac *.webm *.mkv"), ("すべてのファイル", "*.*")]
        )
        if files:
            self.selected_files = list(files)
            self.file_entry.delete(0, "end")
            self.file_entry.insert(0, ", ".join(os.path.basename(f) for f in files))

    def open_songs_folder(self):
        p = get_minecraft_songs_dir()
        os.makedirs(p, exist_ok=True)
        if sys.platform == "win32":
            os.startfile(p)
        elif sys.platform == "darwin":
            subprocess.run(["open", p])
        else:
            subprocess.run(["xdg-open", p])

    def start_convert(self):
        text = self.file_entry.get().strip()
        if not text and not self.selected_files:
            messagebox.showwarning("警告", "変換するファイルを選択してください。")
            return

        # 手入力された場合の処理
        files = self.selected_files
        if not files:
            files = [p.strip() for p in text.split(",") if os.path.isfile(p.strip())]

        if not files:
            messagebox.showerror("エラー", "指定されたファイルが見つかりません。")
            return

        cmd = [sys.executable, os.path.join(HERE, "convert.py")]
        cmd += files
        if not self.var_install.get():
            cmd.append("--no-install")
        if self.var_separate.get():
            cmd.append("--separate")
        if self.var_drums.get():
            cmd.append("--drums")

        speed = self.entry_speed.get().strip()
        if speed and speed != "1.0":
            cmd += ["--speed", speed]
        trans = self.entry_trans.get().strip()
        if trans and trans != "0":
            cmd += ["--transpose", trans]

        self.btn_run.config(state="disabled")
        self.txt_log.delete("1.0", "end")
        self.log("変換を開始します: " + " ".join(cmd))

        def worker():
            try:
                proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
                for line in proc.stdout:
                    self.root.after(0, self.log, line.rstrip())
                proc.wait()
                if proc.returncode == 0:
                    self.root.after(0, messagebox.showinfo, "完了", "曲の変換が完了しました！\nMinecraft内の選曲台から曲を選んで建築できます。")
                else:
                    self.root.after(0, messagebox.showerror, "エラー", f"変換中にエラーが発生しました (終了コード {proc.returncode})。")
            except Exception as e:
                self.root.after(0, self.log, f"例外が発生しました: {e}")
                self.root.after(0, messagebox.showerror, "エラー", str(e))
            finally:
                self.root.after(0, lambda: self.btn_run.config(state="normal"))

        threading.Thread(target=worker, daemon=True).start()


def main():
    root = tk.Tk()
    app = ConverterApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
