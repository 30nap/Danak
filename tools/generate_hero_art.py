"""
Generates Danak's hero artwork.

No image source is reachable from this environment, so each hero is drawn from a motif
chosen for its own Danak: a node graph for an unfinished-loop memory effect, orbital
paths for tidal locking, stratified bands for the fall of Rome. The look is shared —
near-black ground, one category accent, soft light — so the feed reads as one product.
"""
import math, random, os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageChops
import numpy as np

W, H = 1080, 1620
S = 2                      # supersample factor
SW, SH = W * S, H * S

def hexc(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))

def ground(accent, warm=0.0):
    """Near-black base with an off-centre bloom of the accent hue."""
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    cx, cy = W * 0.62, H * 0.30
    r = np.sqrt(((xx - cx) / (W * 0.95)) ** 2 + ((yy - cy) / (H * 0.80)) ** 2)
    g = np.clip(1.0 - r, 0.0, 1.0) ** 2.1
    base = np.array([6, 8, 9], np.float32)
    tint = np.array(accent, np.float32) * 0.17 + np.array([10, 12, 14], np.float32)
    tint = tint * (1.0 - warm) + np.array([34, 24, 18], np.float32) * warm
    img = base[None, None, :] + g[..., None] * (tint - base)[None, None, :]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB')

def glow(layer, radius, gain):
    b = layer.filter(ImageFilter.GaussianBlur(radius))
    return ImageChops.add(layer, b.point(lambda v: min(255, int(v * gain))))

def compose(base, strokes, blooms):
    """strokes: crisp marks. blooms: (layer, blur, gain) soft light added on top."""
    out = base
    for layer, rad, gain in blooms:
        small = layer.resize((W, H), Image.LANCZOS)
        out = ImageChops.screen(out, glow(small, rad, gain))
    if strokes is not None:
        out = ImageChops.screen(out, strokes.resize((W, H), Image.LANCZOS))
    return out

def finish(img, seed):
    a = np.asarray(img).astype(np.float32)
    # vignette
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    r = np.sqrt(((xx - W / 2) / (W * 0.72)) ** 2 + ((yy - H / 2) / (H * 0.72)) ** 2)
    a *= np.clip(1.10 - 0.42 * r ** 2.0, 0.35, 1.0)[..., None]
    # fine grain keeps large flat gradients from banding on OLED panels
    rng = np.random.default_rng(seed)
    a += rng.normal(0, 2.6, (H, W, 1))
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGB')

def canvas():
    im = Image.new('RGB', (SW, SH), (0, 0, 0))
    return im, ImageDraw.Draw(im)

# ---------------------------------------------------------------- motifs

def m_open_loops(d, ac, rng):
    """Unfinished loops — arcs that never close."""
    for i in range(9):
        rad = (0.10 + i * 0.085) * SW
        cx, cy = SW * 0.52, SH * 0.40
        box = (cx - rad, cy - rad, cx + rad, cy + rad)
        start = rng.uniform(0, 360)
        sweep = rng.uniform(190, 305)
        t = 1.0 - i / 10.0
        d.arc(box, start, start + sweep, fill=lerp((0, 0, 0), ac, 0.35 + 0.6 * t),
              width=int(2 * S + 3 * S * t))

def m_confidence_curve(d, ac, rng):
    pts = []
    for i in range(260):
        x = i / 259.0
        y = 0.92 * math.exp(-((x - 0.07) ** 2) / 0.006) + 0.20 + 0.55 * x ** 1.7
        pts.append((SW * (0.08 + x * 0.84), SH * (0.62 - y * 0.30)))
    d.line(pts, fill=ac, width=5 * S, joint='curve')
    for gx in range(1, 7):
        x = SW * (0.08 + gx / 7.0 * 0.84)
        d.line([(x, SH * 0.18), (x, SH * 0.66)], fill=lerp((0, 0, 0), ac, 0.13), width=S)

def m_filtered_rays(d, ac, rng):
    ox, oy = SW * 0.70, -SH * 0.10
    for i in range(16):
        a = math.radians(108 + i * 3.4 + rng.uniform(-1, 1))
        w = rng.uniform(0.010, 0.032) * SW
        L = SH * 1.05
        x2, y2 = ox + math.cos(a) * L, oy + math.sin(a) * L
        keep = i % 3 != 1
        c = lerp((0, 0, 0), ac, 0.22 if keep else 0.05)
        d.line([(ox, oy), (x2, y2)], fill=c, width=int(w))

def m_spectrum(d, ac, rng):
    cols = [hexc(c) for c in ('#6FB8FF', '#7BE0D6', '#A9E06F', '#FFD36E', '#FF9E6E')]
    for i in range(420):
        t = rng.random()
        x = SW * (0.05 + t * 0.9)
        y = SH * (0.16 + rng.random() ** 1.4 * 0.5)
        r = (1.0 - t) ** 2 * 9 * S + 1.6 * S
        c = cols[min(len(cols) - 1, int(t * len(cols)))]
        d.ellipse((x - r, y - r, x + r, y + r), fill=lerp((0, 0, 0), c, 0.30 + 0.6 * (1 - t)))

def m_diffusion(d, ac, rng):
    """Order on the left dissolving into disorder — entropy, drawn literally."""
    for row in range(26):
        y0 = SH * (0.12 + row * 0.0185)
        for col in range(34):
            t = col / 33.0
            jx = rng.normal(0, 1) * t ** 2 * SW * 0.055
            jy = rng.normal(0, 1) * t ** 2 * SH * 0.030
            x = SW * (0.06 + col * 0.0265) + jx
            y = y0 + jy
            r = 3.0 * S
            d.ellipse((x - r, y - r, x + r, y + r),
                      fill=lerp((0, 0, 0), ac, 0.75 - 0.35 * t))

def m_orbit(d, ac, rng):
    cx, cy = SW * 0.50, SH * 0.36
    for i, rr in enumerate((0.16, 0.25, 0.35, 0.46)):
        rad = rr * SW
        d.ellipse((cx - rad, cy - rad * 0.42, cx + rad, cy + rad * 0.42),
                  outline=lerp((0, 0, 0), ac, 0.45 - i * 0.07), width=int(2.2 * S))
    d.ellipse((cx - 0.085 * SW, cy - 0.085 * SW, cx + 0.085 * SW, cy + 0.085 * SW),
              fill=lerp((0, 0, 0), ac, 0.9))
    mx, my = cx + 0.35 * SW, cy + 0.06 * SW
    d.ellipse((mx - 0.030 * SW, my - 0.030 * SW, mx + 0.030 * SW, my + 0.030 * SW), fill=ac)

def m_circuit(d, ac, rng):
    step = SW / 13.0
    for i in range(13):
        for j in range(19):
            x, y = step * (i + 0.5), step * (j + 0.5)
            if rng.random() < 0.42:
                dx = step * rng.choice([1, -1])
                d.line([(x, y), (x + dx, y)], fill=lerp((0, 0, 0), ac, 0.30), width=int(1.6 * S))
            if rng.random() < 0.42:
                dy = step * rng.choice([1, -1])
                d.line([(x, y), (x, y + dy)], fill=lerp((0, 0, 0), ac, 0.30), width=int(1.6 * S))
            if rng.random() < 0.13:
                r = 3.4 * S
                d.ellipse((x - r, y - r, x + r, y + r), fill=ac)

def m_cells(d, ac, rng):
    cols, rows = 5, 8
    for j in range(rows):
        charge = max(0.0, 1.0 - j / (rows - 0.4))
        for i in range(cols):
            w = SW * 0.155
            x = SW * 0.07 + i * SW * 0.172
            y = SH * 0.10 + j * SH * 0.093
            d.rounded_rectangle((x, y, x + w, y + SH * 0.062), radius=int(10 * S),
                                outline=lerp((0, 0, 0), ac, 0.14), width=int(1.8 * S))
            fw = w * charge * (0.6 + 0.4 * rng.random())
            if fw > 4:
                d.rounded_rectangle((x, y, x + fw, y + SH * 0.062), radius=int(10 * S),
                                    fill=lerp((0, 0, 0), ac, 0.06 + 0.22 * charge))

def m_globe_mesh(d, ac, rng):
    cx, cy, R = SW * 0.5, SH * 0.36, SW * 0.36
    for i in range(9):
        k = (i - 4) / 4.0
        rr = R * math.sqrt(max(0.02, 1 - k * k))
        d.ellipse((cx - R, cy + k * R - rr * 0.16, cx + R, cy + k * R + rr * 0.16),
                  outline=lerp((0, 0, 0), ac, 0.26), width=int(1.6 * S))
    for i in range(12):
        k = (i - 5.5) / 5.5
        rw = R * abs(k) if k else R * 0.02
        d.ellipse((cx - rw, cy - R, cx + rw, cy + R),
                  outline=lerp((0, 0, 0), ac, 0.20), width=int(1.4 * S))
    for _ in range(7):
        a = rng.uniform(0, math.tau)
        rr = R * math.sqrt(rng.random()) * 0.92
        x, y = cx + math.cos(a) * rr, cy + math.sin(a) * rr * 0.98
        r = 6.0 * S
        d.ellipse((x - r, y - r, x + r, y + r), fill=ac)

def m_brackets(d, ac, rng):
    y = SH * 0.12
    for depth in range(9):
        x = SW * (0.10 + depth * 0.055)
        h = SH * (0.62 - depth * 0.062)
        d.line([(x + SW * 0.028, y), (x, y + SW * 0.030), (x, y + h - SW * 0.030),
                (x + SW * 0.028, y + h)], fill=lerp((0, 0, 0), ac, 0.75 - depth * 0.065),
               width=int(3.0 * S), joint='curve')
        y += SH * 0.031

def m_binary_rain(d, ac, rng):
    step = SW / 22.0
    for i in range(22):
        x = step * (i + 0.5)
        n = rng.integers(6, 22)
        y0 = rng.random() * SH * 0.35
        for k in range(int(n)):
            t = 1.0 - k / float(n)
            y = y0 + k * step * 1.25
            if y > SH * 0.80:
                break
            d.rectangle((x - step * 0.14, y, x + step * 0.14, y + step * 0.5),
                        fill=lerp((0, 0, 0), ac, 0.12 + 0.70 * t))

def m_btree(d, ac, rng):
    def node(x, y, w):
        d.rounded_rectangle((x - w / 2, y - SH * 0.017, x + w / 2, y + SH * 0.017),
                            radius=int(7 * S), outline=ac, width=int(2.0 * S))
    levels = [(SH * 0.14, 1, SW * 0.22), (SH * 0.34, 3, SW * 0.17), (SH * 0.54, 7, SW * 0.10)]
    prev = []
    for y, n, w in levels:
        xs = [SW * (i + 1) / (n + 1) for i in range(n)]
        for x in xs:
            node(x, y, w)
        for x in xs:
            if prev:
                px = min(prev, key=lambda p: abs(p - x))
                d.line([(px, y - SH * 0.163), (x, y - SH * 0.019)],
                       fill=lerp((0, 0, 0), ac, 0.32), width=int(1.6 * S))
        prev = xs

def m_bars_trend(d, ac, rng):
    n = 17
    vals = []
    v = 0.28
    for _ in range(n):
        v = max(0.10, min(0.85, v + rng.normal(0.035, 0.085)))
        vals.append(v)
    bw = SW * 0.036
    for i, v in enumerate(vals):
        x = SW * (0.09 + i * 0.049)
        y1 = SH * 0.58
        d.rounded_rectangle((x, y1 - SH * v * 0.42, x + bw, y1), radius=int(5 * S),
                            fill=lerp((0, 0, 0), ac, 0.07 + 0.11 * v))
    pts = [(SW * (0.09 + i * 0.049) + bw / 2, SH * 0.58 - SH * v * 0.42)
           for i, v in enumerate(vals)]
    d.line(pts, fill=lerp((0, 0, 0), ac, 0.62), width=int(3.0 * S), joint='curve')

def m_compound(d, ac, rng):
    """Linear versus compounding, on the same axes."""
    for k in range(1, 6):
        y = SH * (0.66 - k * 0.100)
        d.line([(SW * 0.07, y), (SW * 0.93, y)], fill=lerp((0, 0, 0), ac, 0.10), width=int(S))
    for k in range(1, 8):
        x = SW * (0.07 + k * 0.1075)
        d.line([(x, SH * 0.12), (x, SH * 0.66)], fill=lerp((0, 0, 0), ac, 0.07), width=int(S))
    lin = [(SW * (0.08 + t / 300 * 0.84), SH * (0.66 - t / 300 * 0.20)) for t in range(301)]
    d.line(lin, fill=lerp((0, 0, 0), ac, 0.32), width=int(2.6 * S))
    exp = [(SW * (0.08 + t / 300 * 0.84), SH * (0.66 - (1.0155 ** (t * 0.62) - 1) * 0.021))
           for t in range(301)]
    exp = [(x, y) for x, y in exp if y > SH * 0.08]
    d.line(exp, fill=lerp((0, 0, 0), ac, 0.92), width=int(5.0 * S), joint='curve')
    if exp:
        hx, hy = exp[-1]
        r = 9.0 * S
        d.ellipse((hx - r, hy - r, hx + r, hy + r), fill=ac)

def m_sunk_layers(d, ac, rng):
    """Cost already sunk: each layer paid for, each one narrower than the last."""
    for i in range(12):
        y = SH * (0.14 + i * 0.044)
        w = SW * (0.82 - i * 0.055)
        x = (SW - w) / 2
        t = 1.0 - i / 11.0
        d.rounded_rectangle((x, y, x + w, y + SH * 0.026), radius=int(7 * S),
                            fill=lerp((0, 0, 0), ac, 0.05 + 0.10 * t),
                            outline=lerp((0, 0, 0), ac, 0.18 + 0.34 * t), width=int(1.8 * S))

def m_arches(d, ac, rng):
    """A colonnade losing its arches from right to left — empire, mid-collapse."""
    base = SH * 0.70
    heights = [0.34, 0.33, 0.315, 0.24, 0.15, 0.09]
    intact = [True, True, True, False, False, False]
    for i, (hh, ok) in enumerate(zip(heights, intact)):
        w = SW * 0.132
        x = SW * 0.055 + i * SW * 0.155
        top = base - SH * hh
        c = lerp((0, 0, 0), ac, 0.62 - i * 0.075)
        if ok:
            d.arc((x, top - w * 0.5, x + w, top + w * 0.5), 180, 360,
                  fill=c, width=int(4 * S))
            for side in (x, x + w):
                d.line([(side, top), (side, base)], fill=c, width=int(4 * S))
        else:
            # broken columns: ragged stumps, no span left
            for side in (x, x + w):
                stub = base - SH * hh * rng.uniform(0.55, 1.0)
                d.line([(side, stub), (side, base)], fill=c, width=int(4 * S))
                d.line([(side - w * 0.10, stub + SH * 0.006), (side + w * 0.12, stub)],
                       fill=c, width=int(3 * S))
    d.line([(SW * 0.02, base), (SW * 0.98, base)],
           fill=lerp((0, 0, 0), ac, 0.34), width=int(3 * S))
    for _ in range(6):
        x = rng.uniform(SW * 0.50, SW * 0.98)
        y = base + rng.uniform(2, SH * 0.02)
        r = rng.uniform(2, 6) * S
        d.ellipse((x - r, y - r, x + r, y + r), fill=lerp((0, 0, 0), ac, 0.22))

def m_type_grid(d, ac, rng):
    cw, ch = SW * 0.058, SH * 0.050
    for r in range(11):
        for c in range(14):
            x, y = SW * 0.06 + c * cw, SH * 0.12 + r * ch
            if rng.random() < 0.80:
                d.rounded_rectangle((x, y, x + cw * 0.72, y + ch * 0.66), radius=int(4 * S),
                                    outline=lerp((0, 0, 0), ac, 0.18 + 0.28 * rng.random()),
                                    width=int(1.5 * S))
                if rng.random() < 0.30:
                    d.rectangle((x + cw * 0.14, y + ch * 0.16, x + cw * 0.58, y + ch * 0.50),
                                fill=lerp((0, 0, 0), ac, 0.42))

def m_routes(d, ac, rng):
    """A trade network: many hubs, many crossing legs between them."""
    hubs = [(SW * rng.uniform(0.06, 0.94), SH * rng.uniform(0.10, 0.66)) for _ in range(14)]
    hubs.sort(key=lambda p: p[0])
    legs = [(i, i + 1) for i in range(len(hubs) - 1)]
    for _ in range(9):
        i = int(rng.integers(0, len(hubs) - 2))
        j = int(rng.integers(i + 2, len(hubs)))
        legs.append((i, j))
    for i, j in legs:
        (x1, y1), (x2, y2) = hubs[i], hubs[j]
        mx, my = (x1 + x2) / 2, (y1 + y2) / 2 - abs(x2 - x1) * rng.uniform(0.18, 0.42)
        pts = []
        for t in [k / 44 for k in range(45)]:
            bx = (1 - t) ** 2 * x1 + 2 * (1 - t) * t * mx + t * t * x2
            by = (1 - t) ** 2 * y1 + 2 * (1 - t) * t * my + t * t * y2
            pts.append((bx, by))
        d.line(pts, fill=lerp((0, 0, 0), ac, 0.26 + 0.28 * rng.random()),
               width=int(rng.uniform(2.2, 4.0) * S), joint='curve')
    for k, (x, y) in enumerate(hubs):
        r = rng.uniform(4.0, 9.0) * S
        d.ellipse((x - r, y - r, x + r, y + r), fill=lerp((0, 0, 0), ac, 0.55 + 0.45 * rng.random()))

def m_time_blocks(d, ac, rng):
    for r in range(9):
        y = SH * (0.12 + r * 0.056)
        x = SW * 0.08
        while x < SW * 0.90:
            w = SW * rng.uniform(0.06, 0.26)
            if x + w > SW * 0.90:
                w = SW * 0.90 - x
            filled = rng.random() < 0.55
            d.rounded_rectangle((x, y, x + w, y + SH * 0.030), radius=int(8 * S),
                                fill=lerp((0, 0, 0), ac, 0.17) if filled else None,
                                outline=lerp((0, 0, 0), ac, 0.16) if not filled else None,
                                width=int(1.6 * S))
            x += w + SW * 0.018

def m_repetition(d, ac, rng):
    for k in range(6):
        amp = SH * (0.085 - k * 0.010)
        decay = 1.0 + k * 0.9
        y0 = SH * (0.16 + k * 0.083)
        pts = []
        for i in range(340):
            t = i / 339.0
            y = y0 + amp * math.exp(-t * decay) * math.cos(t * math.tau * (2.2 + k * 0.5))
            pts.append((SW * (0.07 + t * 0.86), y))
        d.line(pts, fill=lerp((0, 0, 0), ac, 0.65 - k * 0.075), width=int(2.6 * S), joint='curve')

def m_switch(d, ac, rng):
    lanes = [SH * 0.22, SH * 0.40, SH * 0.58]
    for y in lanes:
        d.line([(SW * 0.06, y), (SW * 0.94, y)], fill=lerp((0, 0, 0), ac, 0.16), width=int(2 * S))
    y = lanes[0]
    x = SW * 0.06
    pts = [(x, y)]
    while x < SW * 0.94:
        x += SW * rng.uniform(0.07, 0.16)
        ny = lanes[int(rng.integers(0, 3))]
        pts += [(x, y), (x + SW * 0.030, ny)]
        y = ny
        x += SW * 0.030
    d.line(pts, fill=ac, width=int(3.4 * S), joint='curve')

def m_hexcomb(d, ac, rng):
    R = SW * 0.062
    for row in range(16):
        for col in range(11):
            cx = SW * 0.04 + col * R * 1.74 + (R * 0.87 if row % 2 else 0)
            cy = SH * 0.08 + row * R * 1.50
            if cy > SH * 0.80:
                continue
            pts = [(cx + R * math.cos(math.radians(60 * k + 30)),
                    cy + R * math.sin(math.radians(60 * k + 30))) for k in range(6)]
            t = rng.random()
            d.polygon(pts, outline=lerp((0, 0, 0), ac, 0.16 + 0.42 * t), width=int(1.8 * S))

def m_heatflow(d, ac, rng):
    for i in range(30):
        y = SH * (0.10 + i * 0.021)
        pts = []
        for k in range(200):
            t = k / 199.0
            amp = SH * 0.012 * (1 - t) ** 1.5
            pts.append((SW * (0.05 + t * 0.90),
                        y + amp * math.sin(t * math.tau * 3 + i * 0.5)))
        fast = i < 12
        d.line(pts, fill=lerp((0, 0, 0), ac, (0.62 if fast else 0.10)),
               width=int((3.4 if fast else 1.2) * S), joint='curve')

def m_contours(d, ac, rng):
    """Bathymetric isolines: clean nested rings that get darker as they go deeper."""
    centres = [(SW * 0.36, SH * 0.30), (SW * 0.72, SH * 0.52)]
    phase = [rng.uniform(0, math.tau) for _ in range(6)]
    for (cx, cy) in centres:
        for k in range(14):
            rad = SW * (0.045 + k * 0.047)
            pts = []
            for i in range(241):
                a = i / 240.0 * math.tau
                wob = 1.0 + sum(0.055 / (j + 1) * math.sin(a * (j + 2) + phase[j] + k * 0.22)
                                for j in range(5))
                pts.append((cx + math.cos(a) * rad * wob,
                            cy + math.sin(a) * rad * wob * 0.82))
            depth = 1.0 - k / 13.0
            d.line(pts + [pts[0]], fill=lerp((0, 0, 0), ac, 0.10 + 0.42 * depth),
                   width=int((1.4 + 1.6 * depth) * S), joint='curve')

MOTIFS = {
    'open_loops': m_open_loops, 'confidence_curve': m_confidence_curve,
    'filtered_rays': m_filtered_rays, 'spectrum': m_spectrum, 'diffusion': m_diffusion,
    'orbit': m_orbit, 'circuit': m_circuit, 'cells': m_cells, 'globe_mesh': m_globe_mesh,
    'brackets': m_brackets, 'binary_rain': m_binary_rain, 'btree': m_btree,
    'bars_trend': m_bars_trend, 'compound': m_compound, 'sunk_layers': m_sunk_layers,
    'arches': m_arches, 'type_grid': m_type_grid, 'routes': m_routes,
    'time_blocks': m_time_blocks, 'repetition': m_repetition, 'switch': m_switch,
    'hexcomb': m_hexcomb, 'heatflow': m_heatflow, 'contours': m_contours,
}

def render(name, motif, accent_hex, seed, warm=0.0, intensity=1.0, out_dir='.'):
    rng = np.random.default_rng(seed)
    ac = hexc(accent_hex)
    im, d = canvas()
    MOTIFS[motif](d, ac, rng)
    base = ground(ac, warm)
    img = compose(base, None, [(im, 26, 1.25 * intensity), (im, 7, 0.95 * intensity)])
    img = finish(img, seed)
    path = os.path.join(out_dir, f'{name}.webp')
    img.save(path, 'WEBP', quality=84, method=6)
    return path, os.path.getsize(path)

if __name__ == '__main__':
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    import json
    spec = json.load(open(sys.argv[2]))
    total = 0
    for s in spec:
        p, sz = render(s['name'], s['motif'], s['accent'], s['seed'],
                       s.get('warm', 0.0), s.get('intensity', 1.0), out)
        total += sz
        print(f"{s['name']:<28} {s['motif']:<18} {sz/1024:6.0f} KB")
    print(f"total {total/1024/1024:.2f} MB across {len(spec)} images")
