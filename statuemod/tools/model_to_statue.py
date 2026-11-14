#!/usr/bin/env python3
"""3D モデル(OBJ / glb / gltf / PLY / STL など)を、色つきの像の JSON にする。

使い方:
  python3 model_to_statue.py モデル.glb --height 261 -o 像.json
  python3 model_to_statue.py モデル.obj --height 150 --up z --rotate-y 180 --preview p.png

必要なもの: Python 3、pip install trimesh numpy pillow
オプション:
  --height N        … 像の高さ(ブロック)。150 ブロック = 1 m なら、身長 174 cm は 261
  --up y|z          … モデルの「上」の軸(既定 y。Blender から出した OBJ などは z のことが多い)
  --rotate-y 度     … 正面の向きを回す(0 / 90 / 180 / 270)。像の正面(プレイヤー側)は、モデルの +Z 向き(glTF の標準)
  --flip-x          … 左右を入れかえる
  --thick N         … 表面の殻の厚さ。2(既定)で、中は空洞の厚さ 2 の殻。1 だと薄く、すきまが出ることがある
  --samples N       … 1 ブロックの面あたり、表面を何点サンプルするか(既定 10。穴があいたら増やす)
  --preview p.png   … 正面・右・背面・上から見た完成予想(向きや大きさの確認に)
色は、モデルのテクスチャ・頂点の色・材質の色から取り、近いブロックに置きかえます(色のない STL は灰色)。"""
import sys, os, math, argparse
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import blocklib


def load_meshes(path):
    import trimesh
    obj = trimesh.load(path)
    if isinstance(obj, trimesh.Scene):
        try:
            meshes = obj.dump(concatenate=False)
        except TypeError:
            meshes = obj.dump()
    else:
        meshes = [obj]
    return [m for m in meshes if isinstance(m, trimesh.Trimesh) and len(m.faces)]


def sample_colored(mesh, n):
    import trimesh
    try:
        pts, face, col = trimesh.sample.sample_surface(mesh, n, sample_color=True)
        col = np.asarray(col)[:, :3].astype(float)
    except Exception:
        pts, face = trimesh.sample.sample_surface(mesh, n)
        col = np.full((len(pts), 3), 185.0)
    return np.asarray(pts, float), col


def main():
    ap = argparse.ArgumentParser(description="3D モデル → 色つきの像の JSON")
    ap.add_argument("model")
    ap.add_argument("-o", "--out")
    ap.add_argument("--height", type=int, default=200)
    ap.add_argument("--up", choices=["y", "z"], default="y")
    ap.add_argument("--rotate-y", type=int, default=0)
    ap.add_argument("--flip-x", action="store_true")
    ap.add_argument("--thick", type=int, default=2)
    ap.add_argument("--samples", type=int, default=10)
    ap.add_argument("--preview")
    ap.add_argument("--name")
    a = ap.parse_args()

    meshes = load_meshes(a.model)
    if not meshes:
        raise SystemExit("モデルを読めませんでした")
    # 向きをそろえる: y が上、正面は +z
    def orient(p):
        p = p.copy()
        if a.up == "z":
            p = np.stack([p[:, 0], p[:, 2], -p[:, 1]], 1)
        t = math.radians(a.rotate_y if hasattr(a, "rotate_y") else 0)
        t = math.radians(a.rotate_y)
        c, s = math.cos(t), math.sin(t)
        p = np.stack([p[:, 0] * c + p[:, 2] * s, p[:, 1], -p[:, 0] * s + p[:, 2] * c], 1)
        if a.flip_x:
            p[:, 0] = -p[:, 0]
        return p
    allv = np.concatenate([orient(m.vertices) for m in meshes])
    lo, hi = allv.min(0), allv.max(0)
    scale = a.height / (hi[1] - lo[1])
    size = np.ceil((hi - lo) * scale + 1e-6).astype(int) + 1
    print("モデルの大きさ(ブロック): 幅 %d × 高さ %d × 奥行き %d" % (size[0], size[1], size[2]))
    if size.max() > 600:
        raise SystemExit("大きすぎます。--height を小さくしてください")

    areas = np.array([m.area for m in meshes])
    total = float(areas.sum()) * scale * scale
    N = int(max(20000, total * a.samples))
    print("表面を %d 点サンプルします(面積 %.0f ブロック²)" % (N, total))
    acc = np.zeros((size[0], size[1], size[2], 3)); cnt = np.zeros(size, float)
    for m, ar in zip(meshes, areas):
        n = max(200, int(N * ar / areas.sum()))
        pts, col = sample_colored(m, n)
        q = np.floor((orient(pts) - lo) * scale).astype(int)
        q = np.clip(q, 0, size - 1)
        flat = np.ravel_multi_index((q[:, 0], q[:, 1], q[:, 2]), size)
        for ch in range(3):
            acc.reshape(-1, 3)[:, ch] += np.bincount(flat, weights=col[:, ch], minlength=cnt.size)
        cnt.reshape(-1)[:] += np.bincount(flat, minlength=cnt.size)
    occ = cnt > 0
    rgb = np.where(occ[..., None], acc / np.maximum(cnt, 1)[..., None], 0)
    # モデルの +z(正面)が手前になるよう、奥行きを反転(z=0 が手前)
    occ = occ[:, :, ::-1]; rgb = rgb[:, :, ::-1]
    if a.thick >= 2:
        occ, rgb = blocklib.thicken(occ, rgb, 1)         # 穴を閉じて、厚さ 2 の殻にする
    idx = np.zeros(occ.shape, int)
    idx[occ] = blocklib.nearest_blocks(rgb[occ])
    out = a.out or os.path.splitext(a.model)[0] + ".json"
    name = a.name or os.path.splitext(os.path.basename(out))[0]
    w, d, h, n, kinds = blocklib.write_statue(out, name, occ, idx, "3D モデルから作った像(model_to_statue.py)。layers の先頭が 1 層目(下)。各層の先頭の行が正面。")
    print("%s: %d × %d × %d ブロック、%d 個、%d 種類" % (out, w, d, h, n, kinds))
    if a.preview:
        blocklib.preview(occ, idx, a.preview)
        print("完成予想:", a.preview)
    print("ゲーム内: .minecraft/statues/ に入れて /statue build %s" % name)


if __name__ == "__main__":
    main()
