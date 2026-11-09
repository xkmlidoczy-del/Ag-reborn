#!/usr/bin/env python3
"""Procedurally generates the effect materials of the per-skill motifs (assets/academy/textures/vfx3/*.png).

All textures are white RGB with the light intensity in alpha (the same convention as the vfx2 set), so the vertex colour tints them and
the additive/alpha render types decide the blend. Everything is derived from seeded noise, so the output is reproducible:
    gpt-sovits/.venv/bin/python source/scripts/generate-motif-textures.py
"""
from pathlib import Path
import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/academy/textures/vfx3'
OUT.mkdir(parents=True, exist_ok=True)


def smooth(t): return t * t * (3 - 2 * t)


def lattice_noise(h, w, fy, fx, rng, wrap_y=True, wrap_x=True):
    """Value noise on an (fy x fx) lattice, smoothly interpolated, periodic when asked."""
    ny, nx = fy + (0 if wrap_y else 1), fx + (0 if wrap_x else 1)
    lat = rng.random((ny, nx))
    y = np.linspace(0, fy, h, endpoint=False)[:, None]; x = np.linspace(0, fx, w, endpoint=False)[None, :]
    y0 = np.floor(y).astype(int); x0 = np.floor(x).astype(int); ty = smooth(y - y0); tx = smooth(x - x0)
    def at(yy, xx): return lat[yy % ny, xx % nx]
    return (at(y0, x0) * (1 - tx) + at(y0, x0 + 1) * tx) * (1 - ty) + (at(y0 + 1, x0) * (1 - tx) + at(y0 + 1, x0 + 1) * tx) * ty


def fbm(h, w, fy, fx, rng, octaves=5, gain=.5, wrap_y=True, wrap_x=True):
    total = np.zeros((h, w)); amp = 1.0; norm = 0.0
    for o in range(octaves):
        total += amp * lattice_noise(h, w, fy * 2 ** o, fx * 2 ** o, rng, wrap_y, wrap_x); norm += amp; amp *= gain
    return total / norm


def save(name, intensity, rgb=None):
    a = np.clip(intensity, 0, 1)
    img = np.zeros(a.shape + (4,), np.uint8)
    img[..., :3] = 255 if rgb is None else rgb
    img[..., 3] = (a * 255 + .5).astype(np.uint8)
    Image.fromarray(img, 'RGBA').save(OUT / f'{name}.png', optimize=True)


def disc(n):
    y, x = np.mgrid[0:n, 0:n]; return np.hypot((x + .5) / n * 2 - 1, (y + .5) / n * 2 - 1)


def make():
    rng = np.random.default_rng(20261024)
    N = 256
    r = disc(N); ang = None
    y, x = np.mgrid[0:N, 0:N]; cx = (x + .5) / N * 2 - 1; cy = (y + .5) / N * 2 - 1; th = np.arctan2(cy, cx)

    # bloom: tight hot core + wide halo, never clipped at the edge
    save('bloom', np.clip(np.exp(-r ** 2 * 18) * 1.0 + np.exp(-r ** 2 * 3.2) * .45, 0, 1) * np.clip(1.15 - r, 0, 1) ** 1.5)

    # plasma orb: turbulent body, filaments swirling around a white-hot core, ragged rim
    t1 = fbm(N, N, 4, 4, rng, 5); t2 = fbm(N, N, 6, 6, rng, 4)
    swirl = .5 + .5 * np.sin(th * 3 + r * 9 + t1 * 7)
    body = np.clip(1 - r * (.9 + .5 * (t2 - .5)), 0, 1) ** 1.3
    fil = np.clip(1 - np.abs(np.sin(th * 5 + r * 14 + t1 * 9)) * 3.2, 0, 1) * np.clip(1 - r, 0, 1) ** .8
    save('plasma_orb', np.clip(body * (.45 + .55 * swirl * t1) + fil * .55 + np.exp(-r ** 2 * 14) * .95, 0, 1) * np.clip(1.08 - r, 0, 1))

    # energy streak (ribbon): u along the length (bright head at u=1, long tail), v across; filament noise stretched along u
    H, W = 64, 256
    u = np.linspace(0, 1, W)[None, :]; v = np.linspace(-1, 1, H)[:, None]
    flow = fbm(H, W, 3, 10, rng, 4, wrap_y=False, wrap_x=True)
    cross = np.exp(-(v / (.34 + .1 * flow)) ** 2 * 2.2)
    taper = np.clip(u, 0, 1) ** 1.6
    save('energy_streak', np.clip(cross * (.7 + .8 * flow) * (.22 + .9 * taper) + np.exp(-(v / .16) ** 2) * taper * .9, 0, 1))

    # lightning strip: a ragged, branching bright line across v, tileable along u
    cen = (fbm(1, W, 1, 5, rng, 4)[0] - .5) * 1.3
    cen2 = (fbm(1, W, 1, 9, rng, 3)[0] - .5) * 1.0
    d1 = np.abs(v - cen[None, :]); d2 = np.abs(v - cen2[None, :] * .8 - .25)
    core = np.exp(-(d1 / .045) ** 2) + .55 * np.exp(-(d2 / .03) ** 2) * (fbm(1, W, 1, 6, rng, 3)[0] > .5)[None, :]
    save('lightning', np.clip(core + np.exp(-(d1 / .24) ** 2) * .32, 0, 1) * np.clip(1.25 - np.abs(v), 0, 1))

    # hex cells: glowing hex edges, per-cell brightness, bright vertices (tiles when the hex size divides the texture)
    s = 42.0; hx = x / s; hy = y / (s * .8660254)
    q = hx - hy * .5; rr = hy
    def hex_dist(px, py):
        px = np.abs(px); py = np.abs(py)
        return np.maximum(py, (py + px * 1.7320508) * .5)
    cell_x = np.floor(x / s + .5 * np.floor(y / (s * .8660254))); cell_y = np.floor(y / (s * .8660254))
    fx = (x / s) % 1.0 - .5; fy = (y / (s * .8660254)) % 1.0 - .5
    shift = (np.floor(y / (s * .8660254)) % 2) * .5
    fx = ((x / s + shift) % 1.0) - .5
    hd = hex_dist(fx * 1.0, fy * .8660254 * 1.15)
    edge = np.exp(-((np.abs(hd - .38)) / .035) ** 2)
    cid = (cell_x * 928371 + cell_y * 364479) % 97 / 97.0
    save('hex_cells', np.clip(edge * (.55 + .45 * cid) + (hd < .38) * (.04 + .1 * cid), 0, 1))

    # shock ring: ragged ring at r ~ .78 with a bright leading edge and a dim wake inside
    jag = fbm(1, 512, 1, 14, rng, 4)[0]; ridx = (((th + np.pi) / (2 * np.pi)) * 512).astype(int) % 512
    rj = .78 + (jag[ridx] - .5) * .1
    lead = np.exp(-((r - rj) / .028) ** 2) + .35 * np.exp(-((r - rj + .05) / .1) ** 2) * (r < rj + .01)
    save('shock_ring', np.clip(lead * (1.05 + .5 * jag[ridx]), 0, 1) * np.clip(1.1 - r, 0, 1))

    # ground cracks: worley-style edge network inside a disc, brightest at the centre, branching outward
    pts = rng.random((46, 2)) * 2 - 1
    d = np.stack([np.hypot(cx - p[0], cy - p[1]) for p in pts])
    d.sort(axis=0); edgew = d[1] - d[0]
    crack = np.exp(-(edgew / .022) ** 2) * np.clip(1 - r ** 1.2, 0, 1) + np.exp(-(edgew / .06) ** 2) * .18 * np.clip(1 - r, 0, 1)
    save('crack', np.clip(crack + np.exp(-r ** 2 * 30) * .8, 0, 1) * np.clip(1.05 - r, 0, 1))

    # smoke puff (alpha blended): soft cloud, white, darkened by the vertex colour
    cl = fbm(N, N, 3, 3, rng, 6, .55)
    save('smoke', np.clip(np.clip(1 - r, 0, 1) ** 1.2 * (.25 + 1.1 * cl) - .12, 0, 1) * .8)

    # spark streak (ember): short bright head with a tail
    sk = np.exp(-(np.linspace(-1, 1, 32)[:, None] / .22) ** 2) * (np.linspace(0, 1, 128)[None, :] ** 2.2)
    save('spark', np.clip(sk * 1.3, 0, 1))

    # anamorphic flare: very wide, thin, with a hot centre and a faint secondary streak
    fl = np.exp(-(np.linspace(-1, 1, 64)[:, None] / .09) ** 2) * np.exp(-(np.abs(np.linspace(-1, 1, 256)[None, :]) / .55) ** 1.6) + \
        .35 * np.exp(-(np.linspace(-1, 1, 64)[:, None] / .3) ** 2) * np.exp(-(np.abs(np.linspace(-1, 1, 256)[None, :]) / .16) ** 2)
    save('flare', np.clip(fl, 0, 1))

    # shard: a thin sliver of glass, bright along its edges and tip
    from PIL import ImageDraw
    sh = Image.new('L', (128, 64), 0); dr = ImageDraw.Draw(sh)
    tri_pts = [(4, 34), (122, 14), (110, 50)]
    dr.polygon(tri_pts, fill=255); fill = gaussian_filter(np.array(sh, float) / 255, .8)
    ed = Image.new('L', (128, 64), 0); de = ImageDraw.Draw(ed)
    de.line(tri_pts + [tri_pts[0]], fill=255, width=2)
    edge_s = gaussian_filter(np.array(ed, float) / 255, 1.0)
    gx = np.linspace(0, 1, 128)[None, :]
    save('shard', np.clip(fill * (.12 + .3 * gx) + edge_s * (.7 + .5 * gx), 0, 1) * (fill + edge_s > .03))

    # vortex: logarithmic spiral arms for wind and pressure (additive), fading to the rim and the eye
    sp = .5 + .5 * np.cos(th * 3 - np.log(r + .04) * 6.5 + fbm(N, N, 3, 3, rng, 3) * 2.2)
    save('vortex', np.clip(sp ** 3 * np.clip(1 - r, 0, 1) ** .8 * np.clip(r * 3.5, 0, 1), 0, 1))

    # circuit: orthogonal traces with junction pads (tileable)
    c = np.zeros((N, N)); g = 32
    cellh = rng.random((N // g, N // g)); cellv = rng.random((N // g, N // g))
    for i in range(N // g):
        for j in range(N // g):
            if cellh[i, j] > .45: c[i * g + g // 2 - 1:i * g + g // 2 + 1, j * g:(j + 1) * g] = 1
            if cellv[i, j] > .45: c[i * g:(i + 1) * g, j * g + g // 2 - 1:j * g + g // 2 + 1] = 1
            if cellh[i, j] > .45 and cellv[i, j] > .45:
                c[i * g + g // 2 - 3:i * g + g // 2 + 3, j * g + g // 2 - 3:j * g + g // 2 + 3] = 1
    save('circuit', np.clip(gaussian_filter(c, 1.1) * 1.4 + gaussian_filter(c, 4) * .5, 0, 1))

    # coordinate lattice: fine grid, plus marks at the crossings, bright axes, soft edge falloff
    gl = np.zeros((N, N)); step = 32
    gl[(y % step) < 1.5] = .45; gl[(x % step) < 1.5] = .45
    pm = ((x % step) < 6) & ((y % step) < 1.5) | ((y % step) < 6) & ((x % step) < 1.5)
    gl[pm] = 1.0
    gl[np.abs(y - N // 2) < 1.2] = .85; gl[np.abs(x - N // 2) < 1.2] = .85
    save('lattice', gaussian_filter(gl, .7) * np.clip(1.25 - r, 0, 1) ** 1.2)

    # chevron arrow ">" glyph with a soft glow
    cy_, cx_ = np.mgrid[0:64, 0:64] / 64.0 * 2 - 1
    dd = np.abs(np.abs(cy_) * .9 - (.7 - cx_ * 1.0)) / 1.35
    chev = np.exp(-(dd / .085) ** 2) * (np.abs(cy_) < .78) * (cx_ > -.55) * (cx_ < .75)
    save('chevron', np.clip(gaussian_filter(chev, .8) * 1.3 + gaussian_filter(chev, 3) * .5, 0, 1))

    # rift: a vertical jagged slit with a white-hot centre and a wide soft halo, narrowing at both ends
    RH, RW = 256, 64
    ry = np.linspace(-1, 1, RH)[:, None]; rx = np.linspace(-1, 1, RW)[None, :]
    wob = (fbm(RH, 1, 6, 1, rng, 4, wrap_x=True)[:, 0] - .5) * .5
    sl = np.exp(-(((rx - wob[:, None]) / (.08 * (1 - ry ** 4) + .015))) ** 2) + .4 * np.exp(-(rx / .55) ** 2) * (1 - np.abs(ry)) ** 2
    save('rift', np.clip(sl, 0, 1) * np.clip(1.05 - np.abs(ry), 0, 1))

    # scan bar: soft leading edge with fine scan lines behind it
    sc = np.clip(np.linspace(0, 1, 256)[None, :] ** 3, 0, 1) * (.6 + .4 * np.sin(np.linspace(0, 1, 64)[:, None] * 40)) \
        * np.exp(-(np.linspace(-1, 1, 64)[:, None] / .85) ** 4)
    save('scan', np.clip(sc, 0, 1))

    # sparkle field: scattered four-point stars of different size (dissipation, luck)
    sf = np.zeros((N, N))
    for _ in range(46):
        px, py = rng.integers(8, N - 8, 2); s2 = rng.random() * 5 + 2
        yy, xx = np.mgrid[-8:9, -8:9]
        sf[py - 8:py + 9, px - 8:px + 9] += np.exp(-((np.abs(xx) * np.abs(yy)) / (s2 * .6)) ** 1) * np.exp(-(xx ** 2 + yy ** 2) / (2 * s2 ** 2)) * (.5 + rng.random() * .5)
    save('sparkle', np.clip(sf, 0, 1))


def make_line():
    # soft line: gaussian across the width, flat along the length (rings, frames, guides)
    v = np.linspace(-1, 1, 32)[:, None]; u = np.ones((1, 64))
    save('soft_line', np.clip(np.exp(-(v / .42) ** 2 * 2.4) * u, 0, 1))


if __name__ == '__main__':
    make_line()
    make()
    print('wrote', len(list(OUT.glob('*.png'))), 'textures to', OUT)
