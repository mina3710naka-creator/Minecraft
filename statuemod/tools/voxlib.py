"""像 JSON を作るための小さなボクセルライブラリ。

座標は連続値で、ボクセル (ix,iy,iz) は [ix,ix+1) の立方体(中心は +0.5)。
x=正面から見て左→右, y=下→上, z=正面(手前, 小さい)→奥(大きい)。
"""
import json, math


class Vox:
    def __init__(self):
        self.c = {}

    # ---- 基本 ----
    def set(self, x, y, z, ch):
        self.c[(int(math.floor(x)), int(math.floor(y)), int(math.floor(z)))] = ch

    def get(self, ix, iy, iz):
        return self.c.get((ix, iy, iz))

    def _put(self, ix, iy, iz, ch):
        if callable(ch):
            ch = ch(ix + .5, iy + .5, iz + .5)
        if ch is not None:
            self.c[(ix, iy, iz)] = ch

    def box(self, x0, x1, y0, y1, z0, z1, ch):
        """整数ボクセル座標の直方体(両端を含む)"""
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self._put(x, y, z, ch)

    def fill(self, bbox, inside, ch):
        (x0, x1), (y0, y1), (z0, z1) = bbox
        for ix in range(int(math.floor(x0)), int(math.ceil(x1)) + 1):
            for iy in range(int(math.floor(y0)), int(math.ceil(y1)) + 1):
                for iz in range(int(math.floor(z0)), int(math.ceil(z1)) + 1):
                    if inside(ix + .5, iy + .5, iz + .5):
                        self._put(ix, iy, iz, ch)

    # ---- 形 ----
    def ellipsoid(self, cx, cy, cz, ax, ay, az, ch, cond=None):
        def inside(x, y, z):
            return ((x - cx) / ax) ** 2 + ((y - cy) / ay) ** 2 + ((z - cz) / az) ** 2 <= 1 and (cond is None or cond(x, y, z))
        self.fill(((cx - ax, cx + ax), (cy - ay, cy + ay), (cz - az, cz + az)), inside, ch)

    def capsule(self, p0, p1, r0, r1, ch, cond=None):
        d = [b - a for a, b in zip(p0, p1)]
        L2 = sum(v * v for v in d) or 1e-9
        R = max(r0, r1)

        def inside(x, y, z):
            p = (x, y, z)
            t = sum((p[i] - p0[i]) * d[i] for i in range(3)) / L2
            t = max(0.0, min(1.0, t))
            r = r0 + (r1 - r0) * t
            q = [p0[i] + d[i] * t for i in range(3)]
            return sum((p[i] - q[i]) ** 2 for i in range(3)) <= r * r and (cond is None or cond(x, y, z))
        self.fill(tuple((min(p0[i], p1[i]) - R, max(p0[i], p1[i]) + R) for i in range(3)), inside, ch)

    def loft(self, keys, ch, grow=0.0):
        """高さごとの楕円断面。keys=[(y, cx, cz, ax, az), ...](y 昇順)。各層の中心高さで線形補間"""
        y_lo, y_hi = keys[0][0], keys[-1][0]
        for iy in range(int(math.floor(y_lo)), int(math.ceil(y_hi))):
            y = iy + .5
            if y < y_lo or y > y_hi:
                continue
            for k in range(len(keys) - 1):
                if keys[k][0] <= y <= keys[k + 1][0]:
                    t = (y - keys[k][0]) / (keys[k + 1][0] - keys[k][0])
                    cx, cz, ax, az = [keys[k][i] + (keys[k + 1][i] - keys[k][i]) * t for i in range(1, 5)]
                    break
            ax += grow; az += grow
            for ix in range(int(math.floor(cx - ax)), int(math.ceil(cx + ax)) + 1):
                for iz in range(int(math.floor(cz - az)), int(math.ceil(cz + az)) + 1):
                    if ((ix + .5 - cx) / ax) ** 2 + ((iz + .5 - cz) / az) ** 2 <= 1:
                        self._put(ix, iy, iz, ch)

    # ---- 前面ペイント ----
    def front_z(self, ix, iy, zmin=-10 ** 9, zmax=10 ** 9):
        zs = [z for (x, y, z) in self.c if x == ix and y == iy and zmin <= z <= zmax]
        return min(zs) if zs else None

    def paint_front(self, x, y, ch, depth=1, zmin=-10 ** 9, zmax=10 ** 9):
        ix, iy = int(math.floor(x)), int(math.floor(y))
        z = self.front_z(ix, iy, zmin, zmax)
        if z is not None:
            for d in range(depth):
                if (ix, iy, z + d) in self.c:
                    self.c[(ix, iy, z + d)] = ch

    # ---- 出力 ----
    def dump(self, name, palette, replace_air=False, comment=None):
        xs = [k[0] for k in self.c]; ys = [k[1] for k in self.c]; zs = [k[2] for k in self.c]
        minx, miny, minz = min(xs), min(ys), min(zs)
        w = max(xs) - minx + 1; h = max(ys) - miny + 1; d = max(zs) - minz + 1
        grid = [[[" "] * w for _ in range(d)] for _ in range(h)]
        for (x, y, z), ch in self.c.items():
            grid[y - miny][z - minz][x - minx] = ch
        used = set(self.c.values())
        missing = used - set(palette)
        if missing:
            raise SystemExit("palette にない文字: %r" % missing)
        pal = {k: v for k, v in palette.items() if k in used}
        out = ["{\n", '  "name": %s,\n' % json.dumps(name, ensure_ascii=False)]
        out.append('  "comment": %s,\n' % json.dumps(
            comment or "layers の先頭が1層目(いちばん下)。各層の先頭の行が像の正面、各行は正面から見て左→右、空白は何も置かない。",
            ensure_ascii=False))
        out.append('  "default_version": 2,\n')
        out.append('  "size": [%d, %d, %d],\n' % (w, d, h))
        out.append('  "palette": {\n' + ",\n".join("    %s: %s" % (json.dumps(k), json.dumps(v)) for k, v in pal.items()) + "\n  },\n")
        out.append('  "layers": [\n')
        layer_txt = []
        for y in range(h):
            rows = ["".join(r).rstrip() for r in grid[y]]
            # 後ろの空行は削る(先頭の行=正面の位置は保つ)
            while len(rows) > 1 and not rows[-1]:
                rows.pop()
            layer_txt.append("    [ " + ", ".join(json.dumps(r) for r in rows) + " ]")
        out.append(",\n".join(layer_txt) + "\n  ]\n}\n")
        return "".join(out), (w, d, h), len(self.c)
