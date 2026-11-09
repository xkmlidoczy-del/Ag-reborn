#!/usr/bin/env python3
"""Procedural, original textures for the canon VFX layer (client/canon/CanonFx*).

Run with the project venv: .venv-qa/bin/python3 scripts/build-canon-fx-textures.py
Output is deterministic. Conventions the renderer relies on (do not change without changing CanonFx*):
  * masks are white with the shape in alpha; the renderer tints them per system
  * ribbon textures (streak, flow, caustic, holo, air, needle, inkflow): u runs along the path (x), v across it (y)
  * feather textures: root at the left edge, tip at the right edge, shaft along the middle
  * flip-books (burst, splash, ink, starburst, feather_drift): 4 columns x 2 rows = 8 frames
  * fold sheets (paper_fold): u across the hinge, v along the sheet
Colour is baked only where the material needs it (feather_iri, bubble_iri, inkflow, paper, paper_fold, blob shading).
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/academy/textures/canon_fx"
OUT.mkdir(parents=True, exist_ok=True)


# ------------------------------------------------------------------------------------------- helpers
def save(name, rgba):
    Image.fromarray(np.clip(rgba * 255 + .5, 0, 255).astype(np.uint8), "RGBA").save(OUT / f"{name}.png", optimize=True)


def grid(w, h=None):
    h = h or w
    y, x = np.mgrid[0:h, 0:w]
    return (x + .5) / w * 2 - 1, (y + .5) / h * 2 - 1


def mask(a, rgb=(1, 1, 1)):
    out = np.ones(a.shape + (4,))
    out[..., 0], out[..., 1], out[..., 2] = rgb
    out[..., 3] = np.clip(a, 0, 1)
    return out


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def value_noise(w, h, cells_x, cells_y, seed):
    """Tileable value noise with independent cell counts on each axis (anisotropic when they differ)."""
    rng = np.random.default_rng(seed)
    lattice = rng.random((cells_y, cells_x))
    ys, xs = np.mgrid[0:h, 0:w]
    gx, gy = xs / w * cells_x, ys / h * cells_y
    x0, y0 = gx.astype(int) % cells_x, gy.astype(int) % cells_y
    x1, y1 = (x0 + 1) % cells_x, (y0 + 1) % cells_y
    fx, fy = smooth(0, 1, gx - np.floor(gx)), smooth(0, 1, gy - np.floor(gy))
    top = lattice[y0, x0] * (1 - fx) + lattice[y0, x1] * fx
    bot = lattice[y1, x0] * (1 - fx) + lattice[y1, x1] * fx
    return top * (1 - fy) + bot * fy


def fbm(w, h, base_x, base_y, octaves, seed, gain=.5):
    total, amp, norm = np.zeros((h, w)), 1.0, 0.0
    for o in range(octaves):
        total += value_noise(w, h, base_x << o, base_y << o, seed + o * 17) * amp
        norm += amp
        amp *= gain
    return total / norm


def voronoi(w, h, points, seed):
    """Tileable F1/F2 distances (in units of the image width) for a jittered point set."""
    rng = np.random.default_rng(seed)
    p = rng.random((points, 2))
    tiles = np.concatenate([p + [dx, dy] for dx in (-1, 0, 1) for dy in (-1, 0, 1)])
    ys, xs = np.mgrid[0:h, 0:w]
    u, v = (xs + .5) / w, (ys + .5) / h
    d = np.sqrt((u[..., None] - tiles[:, 0]) ** 2 + (v[..., None] - tiles[:, 1]) ** 2)
    d.sort(axis=-1)
    return d[..., 0], d[..., 1]


def hsv(h, s, v):
    i = (h * 6).astype(int) % 6
    f = h * 6 - np.floor(h * 6)
    p, q, t = v * (1 - s), v * (1 - f * s), v * (1 - (1 - f) * s)
    return (np.choose(i, [v, q, p, p, t, v]), np.choose(i, [t, v, v, q, p, p]), np.choose(i, [p, p, t, v, v, q]))


def supersampled(size, scale, draw_fn, blur=0.0):
    """Draw with PIL at size*scale on a black L canvas, then downsample to an alpha array in [0,1]."""
    big = Image.new("L", (size[0] * scale, size[1] * scale), 0)
    draw_fn(ImageDraw.Draw(big), scale)
    if blur:
        big = big.filter(ImageFilter.GaussianBlur(blur * scale))
    return np.asarray(big.resize(size, Image.LANCZOS), dtype=np.float64) / 255


def sheet(name, frame_fn, cell=128, cols=4, frames=8):
    """frame_fn(t, cell) -> (h,w,4) rgba or (h,w) alpha; t runs 0..1 over the 8 frames."""
    rows = (frames + cols - 1) // cols
    out = np.zeros((rows * cell, cols * cell, 4))
    for i in range(frames):
        f = frame_fn(i / (frames - 1), cell)
        rgba = f if f.ndim == 3 else mask(f)
        r, c = divmod(i, cols)
        out[r * cell:(r + 1) * cell, c * cell:(c + 1) * cell] = rgba
    save(name, out)


# ------------------------------------------------------------------------------------------- light primitives
def glow():
    # Bright core with a long, physically-shaped bloom tail; fades to exactly zero at the edge.
    x, y = grid(256)
    r = np.hypot(x, y)
    core = np.exp(-(r / .16) ** 2)
    tail = 1 / (1 + (r / .22) ** 2) * .55
    save("glow", mask(np.clip(core * .7 + tail, 0, 1) * smooth(1, .7, r) ** 1.5))


def ring():
    # Thin bright ring with a soft outer bloom and a faint inner haze.
    x, y = grid(256)
    r = np.hypot(x, y)
    line = np.exp(-((r - .8) / .022) ** 2)
    bloom = np.exp(-((r - .8) / .09) ** 2) * .35
    haze = smooth(.8, .2, r) * smooth(0, .8, r) * .06
    save("ring", mask(np.clip(line + bloom + haze, 0, 1) * smooth(1, .95, r)))


def ring2():
    # Sigil ring: a crisp inner circle, a double outer band with 24 ticks and three breaks, soft bloom.
    x, y = grid(512)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    inner = np.exp(-((r - .52) / .009) ** 2)
    band = np.exp(-((r - .84) / .012) ** 2) + np.exp(-((r - .9) / .007) ** 2) * .7
    breaks = smooth(.06, .1, np.abs(((th / (2 * math.pi) * 3) % 1) - .5))   # 0 inside a break, 1 elsewhere
    ticks = np.exp(-(((th / (2 * math.pi) * 24) % 1 - .5) / .06) ** 2) * smooth(.84, .86, r) * smooth(.9, .88, r)
    bloom = (np.exp(-((r - .52) / .05) ** 2) + np.exp(-((r - .87) / .07) ** 2)) * .22
    save("ring2", mask(np.clip(inner + (band + ticks) * breaks + bloom, 0, 1) * smooth(1, .96, r)))


def sparkle():
    # Four long rays, four short diagonal rays, bright core.
    x, y = grid(256)
    r = np.hypot(x, y) + 1e-6
    main = np.exp(-np.abs(x) * 60 - np.abs(y) * 2.6) + np.exp(-np.abs(y) * 60 - np.abs(x) * 2.6)
    xd, yd = (x + y) / math.sqrt(2), (x - y) / math.sqrt(2)
    diag = (np.exp(-np.abs(xd) * 70 - np.abs(yd) * 6) + np.exp(-np.abs(yd) * 70 - np.abs(xd) * 6)) * .5
    core = np.exp(-(r / .07) ** 2) + np.exp(-(r / .2) ** 2) * .3
    save("sparkle", mask(np.clip(main + diag + core, 0, 1) * smooth(1, .9, r)))


def star5():
    # Mind-control star pupil: straight-edged five-point star, bright bevelled rim, softer centre, outer halo.
    x, y = grid(512)
    r = np.hypot(x, y) + 1e-6
    th = np.arctan2(y, x) + math.pi / 2
    half = math.pi / 5
    phi = np.abs(((th + half) % (2 * half)) - half)
    P0, P1 = np.array([.9, 0.0]), .38 * np.array([math.cos(half), math.sin(half)])
    E = P1 - P0
    radius = (P0[0] * E[1] - P0[1] * E[0]) / (np.cos(phi) * E[1] - np.sin(phi) * E[0])
    q = r / radius                      # < 1 inside the star
    body = smooth(1.01, .985, q)
    rim = np.exp(-((q - .95) / .035) ** 2)
    fill = .55 + .45 * smooth(.9, .2, q)  # brighter toward the centre
    halo = np.exp(-((q - 1.05) / .12) ** 2) * .3 * (q > .98)
    save("star5", mask(np.clip(body * fill * .8 + rim * .9 + halo, 0, 1) * smooth(1, .97, r)))


# ------------------------------------------------------------------------------------------- ribbons (u along, v across)
def streak():
    x, y = grid(256, 64)
    a = np.abs(y)
    spine = np.exp(-(a / .12) ** 2)
    body = smooth(1, .25, a) ** 1.4
    save("streak", mask(np.clip(body * .55 + spine * .6, 0, 1)))


def needle():
    # A single hard filament with a long tail and a hot head, tapered at both ends.
    x, y = grid(512, 32)
    u = (x + 1) / 2
    width = .06 + .14 * smooth(0, .8, u)
    core = np.exp(-(np.abs(y) / width) ** 2)
    along = smooth(0, .55, u) * smooth(1, .82, u)
    save("needle", mask(core * along * (.6 + .4 * smooth(.5, .85, u))))


def air():
    # Moving air: several fine parallel filaments of different lengths and offsets, mostly transparent.
    x, y = grid(512, 64)
    u = (x + 1) / 2
    a = np.zeros_like(x)
    rng = np.random.default_rng(11)
    for _ in range(9):
        off, w = rng.uniform(-.7, .7), rng.uniform(.03, .07)
        s, e = sorted(rng.uniform(0, 1, 2))
        if e - s < .3:
            e = min(1, s + .45)
        a += np.exp(-((y - off) / w) ** 2) * smooth(s, s + .12, u) * smooth(e, e - .2, u) * rng.uniform(.35, .9)
    save("air", mask(np.clip(a, 0, 1) * smooth(1, .7, np.abs(y))))


def flow():
    # Water body: noise stretched along the flow, lengthwise striations, brighter foam lips at both edges.
    w, h = 512, 128
    n = fbm(w, h, 2, 4, 5, 3)
    striations = fbm(w, h, 2, 16, 3, 9)
    x, y = grid(w, h)
    a = np.abs(y)
    body = smooth(1, .55, a)
    lip = np.exp(-((a - .78) / .1) ** 2) * (.5 + .5 * n)
    tone = .45 + .35 * n + .2 * striations
    save("flow", mask(np.clip(body * tone + lip * .6, 0, 1) * smooth(1, .9, a)))


def caustic():
    # Lit water surface: a cellular (Voronoi) network of thin bright lines, nothing like a crack or a bolt.
    w = h = 256
    f1, f2 = voronoi(w, h, 22, 31)
    edge = f2 - f1
    net = np.exp(-(edge / .011) ** 2)
    g1, g2 = voronoi(w, h, 40, 32)    # finer second layer
    net2 = np.exp(-((g2 - g1) / .008) ** 2) * .35
    soft = np.exp(-(edge / .05) ** 2) * .14
    x, y = grid(w, h)
    across = smooth(1, .2, np.abs(y))
    save("caustic", mask(np.clip(net + net2 + soft + .05, 0, 1) * across))


def holo():
    # Holographic control thread: crisp dashes with chevrons, a dotted guide, two faint scanlines.
    w, h = 512, 64
    x, y = grid(w, h)
    u = (x + 1) / 2
    seg = (u * 10) % 1
    dash = smooth(.0, .04, seg) * smooth(.62, .58, seg)
    chevron = np.exp(-((np.abs(y) * .6 - (seg - .78)) / .03) ** 2) * (seg > .66) * (seg < .9) * (np.abs(y) < .5)
    core = np.exp(-(np.abs(y) / .09) ** 2) * dash
    dots = np.exp(-(((u * 40) % 1 - .5) / .12) ** 2) * np.exp(-((np.abs(y) - .55) / .05) ** 2) * .55
    scan = np.exp(-((np.abs(y) - .85) / .03) ** 2) * .25
    save("holo", mask(np.clip(core + chevron * .9 + dots + scan, 0, 1)))


def inkflow():
    # Viscous shadow liquid: near-black violet body, a wavering wet highlight, a cold violet rim, small glints.
    w, h = 512, 128
    x, y = grid(w, h)
    u = (x + 1) / 2
    n = fbm(w, h, 3, 2, 4, 41)
    wob = .12 * np.sin(u * math.pi * 6 + n * 3)
    a = np.abs(y)
    alpha = smooth(1, .86, a + .05 * (n - .5))
    spec = np.exp(-((y + .42 + wob) / .07) ** 2) * (.45 + .55 * n)
    glints = np.exp(-(((u * 9 + n * 2) % 1 - .5) / .05) ** 2) * np.exp(-((y + .2) / .08) ** 2) * .5
    rim = np.exp(-((a - .86) / .07) ** 2)
    shade = .04 + .06 * n
    r_ = np.clip(shade + spec * .85 + glints * .7 + rim * .42, 0, 1)
    g_ = np.clip(shade * .9 + spec * .82 + glints * .65 + rim * .26, 0, 1)
    b_ = np.clip(shade * 1.8 + spec * .95 + glints * .85 + rim * .78, 0, 1)
    save("inkflow", np.dstack([r_, g_, b_, alpha * .97]))


# ------------------------------------------------------------------------------------------- feathers (root left, tip right)
def feather_alpha(size=256, seed=7, scale=4):
    """Shaft, a few hundred barbs angled toward the tip, zipper splits, downy base. Returns alpha in [0,1]."""
    rng = np.random.default_rng(seed)
    W = H = size

    def draw(d, s):
        def P(x, y): return (x * W * s, y * H * s)
        def shaft(t): return (.04 + .92 * t, .5 + .05 * math.sin(t * math.pi) - .03 * t)
        splits = {int(v) for v in rng.uniform(80, 240, 2)}
        n = 300
        for i in range(n):
            t = .08 + .9 * i / n
            sx, sy = shaft(t)
            for side, wmax in ((-1, .24), (1, .15)):     # asymmetric vane, like a flight feather
                if i in splits or (i + 1) in splits:
                    continue
                w = wmax * math.sin(min(1, t / .97) * math.pi) ** .45 * (1 - .5 * t) + .006   # long, tapering to a point
                ang = math.radians(24 + 10 * (1 - t))
                ex, ey = sx + w / math.tan(ang) * .55, sy + side * w
                bright = int(255 * (.8 + .2 * rng.random()) * (.88 if side > 0 else 1))
                d.line([P(sx, sy), P(ex, ey)], fill=bright, width=max(1, int(1.2 * s)))
        for i in range(70):                                  # downy, curly base
            t = rng.uniform(.02, .16)
            sx, sy = shaft(t)
            side = rng.choice([-1, 1])
            pts = [P(sx, sy)]
            for k in range(1, 5):
                pts.append(P(sx + k * .012 + rng.normal(0, .006), sy + side * k * .028 + rng.normal(0, .01)))
            d.line(pts, fill=int(255 * rng.uniform(.25, .5)), width=max(1, int(1.0 * s)))
        pts = [P(*shaft(t)) for t in np.linspace(0, 1, 60)]  # shaft: bright, tapering toward the tip
        for k, wdt in enumerate((5, 3.5, 2)):
            d.line(pts[:int(60 * (1 - k * .2))], fill=255, width=max(1, int(wdt * s * .5)))
    return supersampled((W, H), scale, draw, blur=.35)


def feather():
    a = feather_alpha()
    x, y = grid(256)
    u = (x + 1) / 2
    save("feather", mask(np.clip(a * (.8 + .2 * smooth(.0, .5, u)), 0, 1)))


def feather_iri():
    # Same feather; colour disperses from a white shaft to a thin-film rainbow toward the edges and the tip.
    a = feather_alpha()
    x, y = grid(256)
    u = (x + 1) / 2
    off = np.abs(y)
    hue = (u * .7 + off * 1.3 + .55) % 1
    sat = np.clip(off * 1.4 + u * .35, 0, .75)
    r_, g_, b_ = hsv(hue, sat, np.ones_like(u))
    save("feather_iri", np.dstack([r_, g_, b_, np.clip(a, 0, 1)]))


def feather_drift():
    # A small feather tumbling: rotation plus foreshortening, cut from the real feather alpha.
    src = Image.fromarray((feather_alpha(128, seed=13, scale=3) * 255).astype(np.uint8), "L")

    def frame(t, cell):
        ang = math.sin(t * math.pi * 2) * 50 + 20
        squash = .25 + .75 * abs(math.cos(t * math.pi * 2))
        im = src.resize((int(cell * .9), max(2, int(cell * .9 * squash))), Image.LANCZOS).rotate(ang, expand=True, resample=Image.BICUBIC)
        im.thumbnail((cell, cell))
        canvas = Image.new("L", (cell, cell), 0)
        canvas.paste(im, ((cell - im.width) // 2, (cell - im.height) // 2))
        return np.asarray(canvas, dtype=np.float64) / 255
    sheet("feather_drift", frame)


# ------------------------------------------------------------------------------------------- liquids
def blob():
    # Glossy droplet: top-lit body, Fresnel-bright rim, sharp main highlight and a small secondary one.
    x, y = grid(256)
    r = np.hypot(x, y)
    a = smooth(.985, .95, r)
    nz = np.sqrt(np.clip(1 - r ** 2, 0, 1))
    light = np.clip(-x * .35 - y * .55 + nz * .75, 0, 1)
    spec = np.exp(-(((x + .34) ** 2 + (y + .38) ** 2) / .012))
    spec2 = np.exp(-(((x - .38) ** 2 + (y - .42) ** 2) / .004)) * .5
    rim = smooth(.7, .97, r) * .45
    lum = np.clip(.12 + light * .35 + rim + spec * .95 + spec2, 0, 1)
    save("blob", np.dstack([lum, lum, lum, a * .96]))


def bubble_base(iri):
    x, y = grid(256)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    fres = smooth(.55, .97, r) ** 2.2                   # thin in the middle, dense at the silhouette
    edge = smooth(.995, .96, r)
    spec = np.exp(-(((x + .36) ** 2 + (y + .4) ** 2) / .01))
    window = np.exp(-(((x + .2) ** 2 + (y + .5) ** 2) / .02)) * .35
    spec2 = np.exp(-(((x - .42) ** 2 + (y - .45) ** 2) / .003)) * .55
    a = np.clip(fres * .85 + spec + window + spec2 + .03, 0, 1) * edge
    if not iri:
        return mask(a)
    swirl = fbm(256, 256, 3, 3, 4, 77)
    hue = (th / (2 * math.pi) + r * .9 + swirl * .8) % 1
    r_, g_, b_ = hsv(hue, .6 * smooth(.4, .95, r), np.ones_like(r))
    white = np.clip(spec + spec2 + window, 0, 1)
    return np.dstack([np.clip(r_ + white, 0, 1), np.clip(g_ + white, 0, 1), np.clip(b_ + white, 0, 1), a])


def bubble():
    save("bubble", bubble_base(False))


def bubble_iri():
    save("bubble_iri", bubble_base(True))


def metaballs(shape, balls, capsules, threshold=1.0):
    """Soft metaball field; balls=(cx,cy,r), capsules=(x0,y0,x1,y1,r0,r1) in [-1,1] coordinates."""
    x, y = grid(*shape)
    f = np.zeros_like(x)
    for cx, cy, rr in balls:
        f += rr ** 2 / ((x - cx) ** 2 + (y - cy) ** 2 + 1e-4)
    for x0, y0, x1, y1, r0, r1 in capsules:
        dx, dy = x1 - x0, y1 - y0
        L2 = dx * dx + dy * dy + 1e-9
        t = np.clip(((x - x0) * dx + (y - y0) * dy) / L2, 0, 1)
        px, py = x0 + t * dx, y0 + t * dy
        rr = r0 + (r1 - r0) * t
        f += rr ** 2 / ((x - px) ** 2 + (y - py) ** 2 + 1e-4)
    return smooth(threshold * .85, threshold * 1.15, f)


def ink_frame(t, cell):
    # Viscous blot: swells, flings six tendrils that thin into droplets, then surface tension pulls them back.
    grow = math.sin(min(1, t * 1.25) * math.pi / 2)
    back = max(0, (t - .55) / .45)
    reach = (.25 + .5 * grow) * (1 - .8 * back)
    balls = [(0, 0, .2 * grow * (1 - .25 * back) + .02)]
    caps = []
    for k in range(6):
        ang = k * math.pi / 3 + .35 + .2 * math.sin(k * 2.3)
        L = reach * (.7 + .3 * ((k * 37) % 7) / 7)
        ex, ey = math.cos(ang) * L, math.sin(ang) * L
        caps.append((0, 0, ex, ey, .07 * grow, .015 + .01 * (1 - back)))
        balls.append((ex * 1.08, ey * 1.08, (.035 + .02 * ((k * 5) % 3)) * grow * (1 - .6 * back)))
    a = metaballs((cell, cell), balls, caps, threshold=1.2)
    x, y = grid(cell)
    return a * smooth(1, .93, np.hypot(x, y))


def splash_frame(t, cell):
    # Water crown seen from the side: a rising curved sheet whose rim breaks into droplets that arc out and fall.
    x, y = grid(cell)
    h = math.sin(min(1, t * 1.3) * math.pi) * .8
    spread = .3 + .6 * t
    base = .7
    wall_y = base - h * (1 - (x / max(spread, .05)) ** 2)
    in_wall = (np.abs(x) < spread) & (y > wall_y) & (y < base + .04)
    wall = in_wall * smooth(spread, spread * .7, np.abs(x)) * (1 - t * .6)
    lip = np.exp(-((y - wall_y) / .03) ** 2) * (np.abs(x) < spread) * (1 - t * .5)
    balls = []
    for k in range(9):
        side = (k / 8) * 2 - 1
        vx, vy = side * (.55 + .25 * (k % 3) / 2), -(1.25 - .3 * abs(side))
        tt = max(0, t - .12)
        balls.append((side * spread * .9 + vx * tt, base - h + vy * tt + 1.6 * tt * tt, .045 * (1 - t * .5)))
    drops = metaballs((cell, cell), balls, [], threshold=1.0)
    ripple = np.exp(-((np.hypot(x, (y - base - .08) * 4) - (.3 + .6 * t)) / .05) ** 2) * (1 - t)
    sides = np.exp(-((np.abs(x) - spread * .92) / .05) ** 2) * in_wall * (1 - t * .5)   # a hollow crown: thin sheet, bright flanks
    return np.clip(wall * .22 + sides * .8 + lip + drops + ripple * .6, 0, 1) * smooth(1, .94, np.hypot(x, y) * .9)


# ------------------------------------------------------------------------------------------- impacts and glyphs
def burst_frame(t, cell):
    # Impact: thin shock ring with bloom, sixteen tapered radial streaks that detach, scattered debris sparks.
    x, y = grid(cell)
    r = np.hypot(x, y) + 1e-6
    th = np.arctan2(y, x)
    rad = .1 + .82 * (1 - (1 - t) ** 2)
    fade = (1 - t) ** 1.2
    ring_ = np.exp(-((r - rad) / (.03 + .05 * (1 - t))) ** 2) * fade
    bloom = np.exp(-((r - rad) / .14) ** 2) * .25 * fade
    lane = np.abs(((th / (2 * math.pi) * 16) % 1) - .5) * 2           # 0 on a streak axis
    inner, outer = rad * (.35 + .4 * t), rad * (.95 + .1 * t)
    streaks = smooth(.25, .0, lane) * smooth(inner, inner + .08, r) * smooth(outer, outer - .1, r) * fade
    rng = np.random.default_rng(5)
    sparks = np.zeros_like(r)
    for _ in range(14):
        a0, sp = rng.uniform(0, 2 * math.pi), rng.uniform(.7, 1.1)
        sparks += np.exp(-(((x - math.cos(a0) * rad * sp) ** 2 + (y - math.sin(a0) * rad * sp) ** 2) / .0012)) * fade
    core = np.exp(-(r / (.12 * (1 - t) + .03)) ** 2) * (1 - t) ** 2.5 * .45   # small, quickly fading: a ring, not a disc
    return np.clip(ring_ + bloom + streaks * .8 + sparks + core, 0, 1) * smooth(1, .93, r)


def starburst_frame(t, cell):
    # The star pupil blooms, turns a fifth of a turn, throws two concentric pulses and shrinks back.
    x, y = grid(cell)
    r = np.hypot(x, y) + 1e-6
    th = np.arctan2(y, x) + math.pi / 2 + t * 1.25
    half = math.pi / 5
    phi = np.abs(((th + half) % (2 * half)) - half)
    P0, P1 = np.array([.95, 0.0]), .4 * np.array([math.cos(half), math.sin(half)])
    E = P1 - P0
    radius = (P0[0] * E[1]) / (np.cos(phi) * E[1] - np.sin(phi) * E[0])
    size = smooth(0, .3, t) * (1 - smooth(.7, 1, t) * .55) * .8
    q = r / (radius * size + 1e-3)
    body = smooth(1.02, .97, q) * (.55 + .45 * smooth(.9, .2, q))
    rim = np.exp(-((q - .95) / .05) ** 2) * (size > .05)
    pulses = sum(np.exp(-((r - (.15 + (t - d) * .9)) / .025) ** 2) * max(0, 1 - (t - d) * 1.4) * (t > d) for d in (0, .3))
    rays = np.exp(-(phi / .04) ** 2) * smooth(.95, .3, r) * (1 - t) * .4
    return np.clip(body + rim * .8 + pulses * .7 + rays, 0, 1) * smooth(1, .96, r)


# ------------------------------------------------------------------------------------------- paper, ice, noise
def paper_texture(w, h, seed):
    fibres = fbm(w, h, 2, 24, 3, seed)          # long fibres running one way
    cross = fbm(w, h, 24, 2, 2, seed + 5) * .4  # a few running across
    grain = fbm(w, h, 32, 32, 2, seed + 9)
    return .82 + .1 * fibres + .05 * cross + .05 * grain


def paper():
    w = h = 256
    lum = paper_texture(w, h, 9)
    x, y = grid(w, h)
    lum = np.clip(lum - smooth(.88, 1, np.maximum(np.abs(x), np.abs(y))) * .1, 0, 1)   # slightly darker deckle edge
    save("paper", np.dstack([lum, lum * .975, lum * .905, np.ones_like(lum)]))


def paper_fold():
    # A folded sheet: two faces lit differently, a sharp valley crease, two faint pre-creases, edge shading.
    w = h = 256
    lum = paper_texture(w, h, 19)
    x, y = grid(w, h)
    face = np.where(x < 0, .8 + .06 * (x + 1), 1.0 - .05 * x)
    crease = np.exp(-(np.abs(x) / .012) ** 2) * .38 - np.exp(-(np.abs(x - .02) / .006) ** 2) * .15
    diag = (np.exp(-(np.abs(y - x * .9 - .1) / .008) ** 2) + np.exp(-(np.abs(y + x * .9 + .3) / .008) ** 2)) * .08
    edge = smooth(.9, 1, np.maximum(np.abs(x), np.abs(y))) * .12
    lum = np.clip(lum * face - crease - diag - edge, 0, 1)
    save("paper_fold", np.dstack([lum, lum * .972, lum * .9, np.ones_like(lum)]))


def noise():
    n = fbm(256, 256, 4, 4, 6, 5)
    n = (n - n.min()) / (n.max() - n.min())
    save("noise", np.dstack([n, n, n, np.ones_like(n)]))


def frost():
    # Ice: six-armed dendrites with side branches over a faint frosted haze; reads as crystal, not as cracks.
    W = H = 256
    rng = np.random.default_rng(23)

    def draw(d, s):
        def arm(x, y, ang, L, depth, width):
            ex, ey = x + math.cos(ang) * L, y + math.sin(ang) * L
            d.line([(x * W * s, y * H * s), (ex * W * s, ey * H * s)], fill=int(255 * (.55 + .45 * depth / 2)), width=max(1, int(width * s)))
            if depth <= 0:
                return
            for k in range(1, 4):
                f = k / 4
                bx, by = x + math.cos(ang) * L * f, y + math.sin(ang) * L * f
                for sgn in (-1, 1):
                    arm(bx, by, ang + sgn * math.pi / 3, L * (.42 - .08 * k), depth - 1, max(.6, width * .6))
        for _ in range(7):
            cx, cy = rng.uniform(.1, .9), rng.uniform(.1, .9)
            L, rot = rng.uniform(.08, .2), rng.uniform(0, math.pi / 3)
            for k in range(6):
                arm(cx, cy, rot + k * math.pi / 3, L, 2, 2.2)
    dend = supersampled((W, H), 3, draw, blur=.3)
    haze = np.asarray(Image.fromarray((fbm(W, H, 4, 4, 4, 29) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(4)), dtype=np.float64) / 255 * .2
    glints = 0
    x, y = grid(W, H)
    across = smooth(1.05, .3, np.abs(y))        # also used as a ribbon: fades toward the long edges
    save("frost", mask(np.clip(dend + haze + glints, 0, 1) * across))


# ------------------------------------------------------------------------------------------- build
if __name__ == "__main__":
    for fn in (glow, ring, ring2, sparkle, star5, streak, needle, air, flow, caustic, holo, inkflow,
               feather, feather_iri, feather_drift, blob, bubble, bubble_iri, paper, paper_fold, noise, frost):
        fn()
    sheet("ink", ink_frame)
    sheet("splash", splash_frame)
    sheet("burst", burst_frame)
    sheet("starburst", starburst_frame)
    for junk in OUT.glob("._*"):
        junk.unlink()
    print("wrote", len([p for p in OUT.glob("*.png") if not p.name.startswith("._")]), "textures to", OUT)
