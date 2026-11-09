#!/usr/bin/env python3
"""Per-skill 32px pictograms for the canon categories, in the same line style as generate-skill-icons.py.

Replaces the earlier category-emblem-plus-badge icons, which players could not tell apart. Each skill gets a
recipe of semantic primitives (who it affects + what it does). Writes skill_icons_v2 and the legacy abilities
copy, then refreshes skill-icons-v2.json hashes. Fails if two skills in one category render identically.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import json, hashlib, math

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/academy/textures'
COLORS = {'mental_out': (244, 145, 204), 'aero_hand': (126, 238, 205), 'nitrogen_armor': (131, 182, 255),
          'dark_matter': (225, 217, 255), 'hydro_hand': (94, 208, 255), 'paper_psychokinesis': (255, 224, 164),
          'liquid_shadow': (166, 173, 198), 'float_dial': (173, 231, 182)}
W = (245, 251, 255); GOLD = (255, 205, 104); ALLY = (255, 214, 128); RED = (255, 120, 110); FIRE = (255, 170, 80)


class Icon:
    def __init__(self, cat, level):
        self.im = Image.new('RGBA', (32, 32)); self.d = ImageDraw.Draw(self.im); self.c = COLORS[cat]
        for x, y, sx, sy in [(1, 1, 1, 1), (30, 1, -1, 1), (1, 30, 1, -1), (30, 30, -1, -1)]:
            self.line([(x, y + sy * 3), (x, y), (x + sx * 3, y)], self.c)
        if level >= 4: self.line([(9, 1), (13, 1), (15, 3), (17, 1), (22, 1)], GOLD)
        if level >= 5: self.line([(10, 30), (14, 30), (16, 28), (18, 30), (22, 30)], GOLD)
        self.emblem(cat)

    # Tiny category mark in the top-left corner, the way the classic icons mark their discipline.
    def emblem(self, cat):
        c = self.c
        if cat == 'mental_out': self.arc(5, 6, 2, 180, 360, c); self.arc(5, 6, 3, 200, 340, c)
        elif cat == 'aero_hand': self.line([(3, 4), (7, 4)], c); self.line([(3, 7), (6, 7)], c)
        elif cat == 'nitrogen_armor': self.poly([(3, 3), (7, 3), (7, 6), (5, 8), (3, 6)], c)
        elif cat == 'dark_matter': self.line([(5, 3), (5, 8)], c); self.line([(3, 5), (7, 5)], c); self.line([(3, 3), (7, 7)], c)
        elif cat == 'hydro_hand': self.poly([(5, 3), (3, 6), (5, 8), (7, 6)], c)
        elif cat == 'paper_psychokinesis': self.poly([(3, 3), (7, 3), (7, 8), (3, 8)], c)
        elif cat == 'liquid_shadow': self.line([(3, 7), (4, 5), (6, 5), (7, 7)], c); self.line([(3, 8), (7, 8)], c)
        elif cat == 'float_dial': self.circle(5, 5, 2, c); self.line([(5, 8), (5, 9)], c)

    # Low-level drawing.
    def line(self, p, c=None, w=1): self.d.line([(round(x), round(y)) for x, y in p], fill=c or self.c, width=w)
    def poly(self, p, c=None): self.line(list(p) + [p[0]], c)
    def box(self, x, y, w, h, c=None): self.d.rectangle((round(x), round(y), round(x + w), round(y + h)), outline=c or self.c)
    def circle(self, x, y, r, c=None): self.d.ellipse((x - r, y - r, x + r, y + r), outline=c or self.c)
    def arc(self, x, y, r, a, b, c=None): self.d.arc((x - r, y - r, x + r, y + r), a, b, fill=c or self.c, width=1)
    def dot(self, x, y, c=None): self.d.point((round(x), round(y)), fill=c or self.c)
    def arrow(self, x, y, X, Y, c=None, s=3):
        c = c or W; self.line([(x, y), (X, Y)], c); a = math.atan2(Y - y, X - x)
        self.line([(X - s * math.cos(a - .7), Y - s * math.sin(a - .7)), (X, Y), (X - s * math.cos(a + .7), Y - s * math.sin(a + .7))], c)
    def dashed(self, x, y, r, c=None, n=10):
        for k in range(n): self.arc(x, y, r, k * 360 / n, k * 360 / n + 360 / n / 2, c or self.c)

    # Subjects.
    def me(self, x=16, y=10, c=None, s=1.0):
        c = c or self.c; self.circle(x, y, 2 * s, c); self.line([(x, y + 3 * s), (x, y + 9 * s)], c)
        self.line([(x - 4 * s, y + 6 * s), (x, y + 4 * s), (x + 4 * s, y + 6 * s)], c)
        self.line([(x - 3 * s, y + 13 * s), (x, y + 9 * s), (x + 3 * s, y + 13 * s)], c)
    def mob(self, x=16, y=10, s=1.0):
        c = self.c; self.box(x - 2 * s, y - 2 * s, 4 * s, 4 * s, c); self.dot(x - 1, y, RED); self.dot(x + 1, y, RED)
        self.line([(x, y + 3 * s), (x, y + 9 * s)], c); self.line([(x - 4 * s, y + 4 * s), (x + 4 * s, y + 4 * s)], c)
        self.line([(x - 3 * s, y + 13 * s), (x, y + 9 * s), (x + 3 * s, y + 13 * s)], c)
    def ally(self, x=16, y=10, s=1.0):
        self.me(x, y, s=s); self.line([(x + 4 * s + 1, y - 2), (x + 4 * s + 5, y - 2)], ALLY); self.line([(x + 4 * s + 3, y - 4), (x + 4 * s + 3, y)], ALLY)
    def item(self, x=16, y=16, r=3, c=None):
        c = c or self.c; self.poly([(x, y - r), (x + r, y - r / 2), (x + r, y + r / 2), (x, y + r), (x - r, y + r / 2), (x - r, y - r / 2)], c)
        self.line([(x - r, y - r / 2), (x, y), (x + r, y - r / 2)], c); self.line([(x, y), (x, y + r)], c)
    def ground(self, y=27, x0=5, x1=27): self.line([(x0, y), (x1, y)], self.c)
    def water(self, y=24, x0=5, x1=27):
        for k in range(x0, x1, 6): self.arc(k + 3, y + 2, 3, 200, 340, self.c)

    # Effects.
    def burst(self, x, y, r=5, c=None, n=8):
        for k in range(n):
            a = k * 2 * math.pi / n; self.line([(x + math.cos(a) * r * .45, y + math.sin(a) * r * .45), (x + math.cos(a) * r, y + math.sin(a) * r)], c or W)
    def shield(self, x=16, y=16, w=12, h=15, c=None):
        c = c or W; x -= w / 2; y -= h / 2
        self.poly([(x, y), (x + w, y), (x + w - 1, y + h * .55), (x + w / 2, y + h), (x + 1, y + h * .55)], c)
    def dome(self, x=16, y=24, r=11, c=None): self.arc(x, y, r, 180, 360, c or W); self.line([(x - r, y), (x + r, y)], c or W)
    def wall(self, x=22, y0=7, y1=25, c=None): self.line([(x, y0), (x, y1)], c or W); self.line([(x + 2, y0), (x + 2, y1)], c or W)
    def blade(self, x=10, y=24, X=24, Y=8, c=None):
        c = c or W; self.line([(x, y), (X, Y)], c); self.line([(x + 1, y - 4), (x + 4, y - 1)], c); self.line([(X - 3, Y), (X, Y), (X, Y + 3)], c)
    def spear(self, x, y, X, Y, c=None): self.arrow(x, y, X, Y, c or W, 4); self.arrow(x, y, X, Y, c or W, 2)
    def shard(self, x, y, r=2, c=None): self.poly([(x, y - r - 1), (x + r, y), (x, y + r + 1), (x - r, y)], c or W)
    def wings(self, x=16, y=16, c=None):
        c = c or W
        for s in (-1, 1):
            self.line([(x + s, y), (x + s * 5, y - 6), (x + s * 12, y - 8), (x + s * 8, y + 1), (x + s * 3, y + 4)], c)
            self.line([(x + s * 4, y - 2), (x + s * 9, y - 4)], self.c)
    def fist(self, x=16, y=16, c=None):
        c = c or W; self.box(x - 4, y - 3, 8, 7, c); self.line([(x - 4, y - 1), (x + 4, y - 1)], c)
        self.line([(x - 1, y - 3), (x - 1, y - 1)], c); self.line([(x + 2, y - 3), (x + 2, y - 1)], c); self.line([(x - 4, y + 4), (x - 6, y + 6)], c)
    def eye(self, x=16, y=12, c=None):
        c = c or W; self.arc(x, y + 3, 7, 205, 335, c); self.arc(x, y - 3, 7, 25, 155, c); self.circle(x, y, 2, c)
    def target(self, x=16, y=16, r=5, c=None):
        c = c or W; self.circle(x, y, r, c); self.dot(x, y, c)
        for dx, dy in [(1, 0), (-1, 0), (0, 1), (0, -1)]: self.line([(x + dx * (r - 1), y + dy * (r - 1)), (x + dx * (r + 2), y + dy * (r + 2))], c)
    def gust(self, x=8, y=10, n=3, c=None, L=14):
        c = c or W
        for k in range(n):
            yy = y + k * 5; self.line([(x, yy), (x + L - k * 3, yy)], c); self.arc(x + L - k * 3, yy - 2, 2, 270, 450, c)
    def spiral(self, x=16, y=16, c=None):
        c = c or W; pts = [(x + math.cos(t / 3) * t * .55, y + math.sin(t / 3) * t * .55) for t in range(0, 22)]; self.line(pts, c)
    def drop(self, x=16, y=16, s=1.0, c=None):
        c = c or W; self.poly([(x, y - 6 * s), (x - 4 * s, y + 1 * s), (x - 3 * s, y + 4 * s), (x, y + 5 * s), (x + 3 * s, y + 4 * s), (x + 4 * s, y + 1 * s)], c)
    def flame(self, x=16, y=18, c=None):
        c = c or FIRE; self.line([(x - 4, y + 4), (x - 5, y), (x - 2, y - 4), (x - 1, y - 1), (x + 1, y - 7), (x + 4, y - 1), (x + 5, y + 3), (x + 3, y + 5), (x - 3, y + 5), (x - 4, y + 4)], c)
    def cross(self, x, y, r=3, c=None): self.line([(x - r, y - r), (x + r, y + r)], c or RED); self.line([(x - r, y + r), (x + r, y - r)], c or RED)
    def bar(self, x, y0, y1, c=None): self.line([(x, y0), (x, y1)], c or RED); self.line([(x + 1, y0), (x + 1, y1)], c or RED)
    def hover(self, x0, x1, y, c=None):
        c = c or W; self.line([(x0, y), (x1, y)], c)
        for k in range(int(x0) + 1, int(x1), 3): self.line([(k, y + 1), (k, y + 2)], c)
    def sheet(self, x, y, w=7, h=9, c=None): c = c or self.c; self.poly([(x, y), (x + w - 2, y), (x + w, y + 2), (x + w, y + h), (x, y + h)], c)
    def plane(self, x=16, y=16, c=None):
        c = c or self.c; self.poly([(x - 8, y + 1), (x + 8, y - 5), (x - 2, y + 6)], c); self.line([(x + 8, y - 5), (x - 1, y + 2), (x - 2, y + 6)], c)
    def steps(self, x, y, n, up=True, c=None):
        c = c or self.c; pts = [(x, y)]
        for k in range(n):
            dy = -4 if up else 4; pts += [(pts[-1][0], pts[-1][1] + dy), (pts[-1][0] + 5, pts[-1][1] + dy)]
        self.line(pts, c)
    def blocks(self, x, y, n, w=4, c=None, vertical=False):
        for k in range(n): self.box(x + (0 if vertical else k * w), y - (k * w if vertical else 0), w, w, c or self.c)
    def ladder(self, x=14, y0=6, y1=26, c=None):
        c = c or W; self.line([(x, y0), (x, y1)], c); self.line([(x + 6, y0), (x + 6, y1)], c)
        for y in range(y0 + 3, y1, 4): self.line([(x, y), (x + 6, y)], c)
    def button(self, x, y, c=None): self.box(x, y, 3, 3, c or RED)
    def plate(self, x, y, w=8, c=None): self.box(x, y, w, 1, c or RED)
    def gate(self, x, y, c=None):
        c = c or W; self.box(x, y, 9, 8, c); self.line([(x + 3, y), (x + 3, y + 8)], c); self.line([(x + 6, y), (x + 6, y + 8)], c)
    def ear(self, x=16, y=14, c=None):
        c = c or W; self.arc(x, y, 6, 180, 420, c); self.line([(x + 3, y + 5), (x, y + 9), (x - 2, y + 8)], c); self.arc(x, y, 2, 180, 360, c)
    def sound(self, x, y, c=None):
        for r in (3, 6): self.arc(x, y, r, 300, 420, c or W)
    def path(self, pts, c=None):
        c = c or W
        for (a, b), (A, B) in zip(pts, pts[1:]):
            L = max(1, int(math.hypot(A - a, B - b) / 3))
            for k in range(0, L, 2): self.line([(a + (A - a) * k / L, b + (B - b) * k / L), (a + (A - a) * (k + 1) / L, b + (B - b) * (k + 1) / L)], c)
    def blob(self, x=16, y=20, c=None):
        c = c or self.c; self.line([(x - 7, y + 3), (x - 6, y - 2), (x - 2, y - 4), (x + 3, y - 4), (x + 6, y - 1), (x + 7, y + 3), (x - 7, y + 3)], c)
        self.dot(x - 2, y - 1, W); self.dot(x + 2, y - 1, W)
    def brain(self, x=16, y=12, c=None):
        c = c or W; self.line([(x, y + 5), (x - 5, y + 4), (x - 7, y + 1), (x - 6, y - 3), (x - 3, y - 5), (x, y - 4), (x + 3, y - 5), (x + 6, y - 3), (x + 7, y + 1), (x + 5, y + 4), (x, y + 5), (x, y - 3)], c)
    def link(self, x0, y0, x1, y1, c=None): self.path([(x0, y0), (x1, y1)], c or RED)
    def crystal(self, x, y, c=None): self.poly([(x, y - 5), (x + 3, y - 1), (x, y + 5), (x - 3, y - 1)], c or self.c); self.line([(x - 3, y - 1), (x + 3, y - 1)], c or self.c)


def R(*steps):
    return lambda i: [s(i) for s in steps]


# Recipes: subjects in category colour, actions in white, hostile cues in red.
RECIPES = {
    # Mental Out: who is read or redirected, and how.
    'mental_probe': lambda i: (i.mob(20, 11), i.eye(10, 10), i.path([(13, 12), (18, 12)])),
    'mental_life_scan': lambda i: (i.eye(16, 7), i.mob(9, 14, .7), i.mob(16, 15, .7), i.mob(23, 14, .7)),
    'mental_intent_scan': lambda i: (i.mob(9, 12), i.arrow(13, 16, 20, 16, RED), i.target(24, 16, 3)),
    'mental_hostile_scan': lambda i: (i.me(22, 11), i.mob(9, 11), i.arrow(12, 15, 18, 15, RED), i.eye(16, 5)),
    'mental_focus_break': lambda i: (i.mob(9, 11), i.path([(13, 18), (19, 18)]), i.cross(23, 18)),
    'mental_disengage': lambda i: (i.me(23, 11), i.mob(8, 11), i.arrow(11, 15, 17, 15, RED), i.bar(19, 11, 19)),
    'mental_route_interrupt': lambda i: (i.mob(7, 10, .8), i.path([(10, 22), (16, 22)]), i.bar(18, 18, 26), i.path([(21, 22), (26, 22)])),
    'mental_attention_shift': lambda i: (i.mob(8, 11), i.me(23, 11), i.arc(16, 18, 7, 200, 340, W), i.arrow(21, 15, 22, 17)),
    'mental_target_sense': lambda i: (i.mob(8, 12, .8), i.mob(24, 12, .8), i.arrow(11, 16, 21, 16, RED), i.eye(16, 6)),
    'mental_disrupt': lambda i: (i.mob(16, 13), i.brain(16, 6), i.cross(24, 6, 2)),
    'mental_interpose': lambda i: (i.mob(7, 12, .8), i.mob(25, 12, .8), i.me(16, 5, s=.6), i.arrow(10, 18, 22, 18, RED), i.arrow(16, 14, 16, 19)),
    'mental_redirect': lambda i: (i.mob(8, 10, .8), i.mob(24, 16, .8), i.line([(11, 14), (16, 14)], RED), i.arrow(16, 14, 21, 19, RED)),
    'mental_pursuit_break': lambda i: (i.me(16, 10), i.mob(6, 14, .7), i.mob(26, 14, .7), i.cross(10, 9, 2), i.cross(22, 9, 2)),
    'mental_suppress_pair': lambda i: (i.mob(10, 11, .8), i.mob(22, 11, .8), i.bar(15, 22, 27), i.hover(6, 26, 8)),
    'mental_conflict': lambda i: (i.mob(7, 11, .8), i.mob(25, 11, .8), i.arrow(10, 15, 15, 15, RED), i.arrow(22, 15, 17, 15, RED)),
    'mental_silence_field': lambda i: (i.me(16, 11), i.dashed(16, 16, 12), i.cross(16, 4, 2)),
    'mental_panic_field': lambda i: (i.mob(16, 10), i.dashed(16, 16, 12), i.arrow(10, 22, 5, 26, RED), i.arrow(22, 22, 27, 26, RED)),
    'mental_lure_pair': lambda i: (i.mob(6, 8, .6), i.mob(6, 19, .6), i.mob(24, 13, .8), i.arrow(10, 11, 19, 15, RED), i.arrow(10, 23, 19, 18, RED)),
    'mental_pursuit_field': lambda i: (i.me(16, 11), i.dashed(16, 16, 12), i.bar(9, 11, 21, W), i.bar(22, 11, 21, W)),
    'mental_out_crowd': lambda i: (i.mob(24, 12, .8), i.mob(6, 6, .55), i.mob(6, 16, .55), i.mob(12, 22, .5), i.arrow(10, 10, 19, 14, RED), i.arrow(10, 20, 19, 17, RED)),
    'mental_crossfire': lambda i: (i.mob(6, 6, .55), i.mob(6, 18, .55), i.mob(26, 6, .55), i.mob(26, 18, .55), i.line([(10, 10), (22, 22)], RED), i.line([(10, 22), (22, 10)], RED)),
    'mental_crowd_detach': lambda i: (i.me(25, 10, s=.8), i.mob(6, 8, .55), i.mob(12, 14, .55), i.mob(6, 20, .55), i.bar(19, 8, 26, W)),
    'mental_conflict_ring': lambda i: (i.circle(16, 16, 10, W), i.mob(16, 4, .45), i.mob(6, 16, .45), i.mob(26, 16, .45), i.mob(16, 20, .45)),
    'mental_relay_interference': lambda i: (i.mob(6, 13, .6), i.mob(16, 13, .6), i.mob(26, 13, .6), i.path([(6, 8), (16, 6), (26, 8)]), i.arrow(22, 7, 26, 9)),
    'mental_puppet': lambda i: (i.mob(16, 14), i.line([(10, 4), (22, 4)], W), i.line([(12, 4), (12, 18)], W), i.line([(20, 4), (20, 18)], W), i.line([(16, 4), (16, 11)], W)),
    # Dark Matter: shards, blades and wings.
    'dark_blade': lambda i: (i.blade(8, 25, 25, 7), i.me(7, 7, s=.5)),
    'dark_shard': lambda i: (i.shard(9, 16, 3), i.path([(13, 16), (19, 16)]), i.mob(24, 11, .8)),
    'dark_feather_guard': lambda i: (i.me(8, 10, s=.8), i.line([(14, 8), (18, 4), (18, 14), (14, 8)], W), i.arrow(28, 9, 20, 9, RED)),
    'dark_step': lambda i: (i.me(9, 9, s=.8), i.wings(9, 14), i.arc(19, 22, 7, 200, 340, W), i.ground()),
    'dark_spread': lambda i: (i.me(6, 11, s=.7), i.shard(16, 8, 2), i.shard(18, 16, 2), i.shard(16, 24, 2), i.line([(10, 16), (26, 6)], W), i.line([(10, 16), (26, 26)], W)),
    'dark_sweep': lambda i: (i.me(16, 9, s=.7), i.arc(16, 18, 11, 200, 340, W), i.arrow(25, 13, 27, 17), i.shard(5, 13, 2)),
    'dark_guard': lambda i: (i.me(16, 9), i.shard(5, 12, 2), i.shard(27, 12, 2), i.shard(16, 27, 2), i.shard(16, 4, 2)),
    'dark_lift': lambda i: (i.me(16, 14, s=.8), i.wings(16, 18), i.arrow(16, 12, 16, 3)),
    'dark_shard_return': lambda i: (i.me(7, 10, s=.8), i.item(24, 20), i.shard(24, 12, 2), i.arrow(20, 20, 12, 20)),
    'dark_wings': lambda i: (i.wings(16, 16), i.me(16, 8, s=.7)),
    'dark_pierce': lambda i: (i.spear(4, 16, 28, 16), i.mob(13, 9, .5), i.mob(21, 9, .5)),
    'dark_shard_orbit': lambda i: (i.item(16, 16), i.circle(16, 16, 9, W), i.shard(25, 16, 2), i.shard(7, 16, 2)),
    'dark_shard_screen': lambda i: (i.me(7, 10, s=.8), i.shard(18, 7, 2), i.shard(18, 14, 2), i.shard(18, 21, 2), i.arrow(28, 14, 22, 14, RED)),
    'dark_wing_brake': lambda i: (i.wings(14, 15), i.arrow(5, 25, 16, 25), i.bar(19, 21, 28)),
    'dark_rain': lambda i: (i.shard(8, 5, 2), i.shard(16, 5, 2), i.shard(24, 5, 2), i.arrow(8, 9, 8, 18), i.arrow(16, 9, 16, 18), i.arrow(24, 9, 24, 18), i.ground(24)),
    'dark_wing_charge': lambda i: (i.wings(10, 14), i.arrow(17, 22, 27, 22), i.burst(26, 13, 4)),
    'dark_panel_dome': lambda i: (i.me(16, 12, s=.8), i.dome(16, 25, 12), i.shard(8, 10, 2), i.shard(24, 10, 2)),
    'dark_shard_salvo': lambda i: (i.shard(6, 16, 3), i.arrow(10, 16, 24, 6), i.arrow(10, 16, 26, 16), i.arrow(10, 16, 24, 26)),
    'dark_wing_rescue': lambda i: (i.me(16, 6, s=.7), i.wings(16, 12), i.arrow(16, 17, 16, 24), i.hover(8, 24, 26)),
    'dark_construct': lambda i: (i.me(16, 11), i.circle(16, 16, 12, W), i.shard(5, 16, 2), i.shard(27, 16, 2), i.shard(16, 4, 2), i.shard(16, 28, 2)),
    'dark_spear_fan': lambda i: (i.spear(6, 16, 26, 5), i.spear(6, 16, 28, 16), i.spear(6, 16, 26, 27)),
    'dark_wing_shelter': lambda i: (i.ally(10, 10), i.wings(22, 14), i.arrow(30, 22, 25, 22, RED)),
    'dark_shard_ring': lambda i: (i.circle(16, 16, 7, W), i.shard(16, 9, 2), i.shard(23, 16, 2), i.shard(9, 16, 2), i.arrow(22, 10, 27, 5), i.arrow(10, 22, 5, 27)),
    'dark_matter_barrage': lambda i: (i.shard(6, 8, 2), i.shard(6, 16, 2), i.shard(6, 24, 2), i.arrow(10, 8, 22, 12), i.arrow(10, 16, 22, 16), i.arrow(10, 24, 22, 20), i.mob(26, 10, .5)),
    'dark_synthesis': lambda i: (i.crystal(7, 16), i.arrow(11, 16, 16, 16), i.item(21, 12, 3), i.item(25, 21, 3)),
    # Nitrogen Armor: shell, pressure hits, guards.
    'nitrogen_armor': lambda i: (i.me(16, 9), i.shield(16, 16, 20, 23)),
    'nitrogen_guard': lambda i: (i.me(8, 10, s=.8), i.line([(16, 6), (19, 9), (19, 21), (16, 24)], W), i.arrow(28, 14, 21, 14, RED)),
    'nitrogen_dash': lambda i: (i.me(19, 9), i.gust(3, 11, 3, W, 9), i.ground()),
    'nitrogen_burst': lambda i: (i.fist(10, 15), i.burst(20, 15, 6), i.mob(26, 9, .5)),
    'nitrogen_brake': lambda i: (i.me(11, 9, s=.8), i.arrow(3, 25, 17, 25), i.bar(20, 6, 27)),
    'nitrogen_ram': lambda i: (i.shield(10, 15, 10, 13), i.arrow(16, 15, 23, 15), i.mob(26, 9, .6), i.ground()),
    'nitrogen_plate': lambda i: (i.me(7, 10, s=.8), i.box(16, 6, 3, 18, W), i.arrow(28, 15, 21, 15, RED)),
    'nitrogen_shove': lambda i: (i.fist(8, 15), i.mob(18, 9, .8), i.arrow(22, 23, 28, 23)),
    'nitrogen_buoy': lambda i: (i.me(16, 5, s=.7), i.shield(16, 10, 10, 10), i.arrow(16, 17, 16, 23), i.arc(16, 26, 6, 200, 340, W)),
    'nitrogen_punch': lambda i: (i.fist(12, 16), i.shield(12, 16, 16, 19, i.c), i.burst(25, 16, 4)),
    'nitrogen_uppercut': lambda i: (i.fist(10, 22), i.arrow(10, 16, 10, 5), i.mob(21, 7, .7), i.arrow(21, 22, 21, 26, W)),
    'nitrogen_sweep': lambda i: (i.shield(16, 8, 8, 9), i.arc(16, 15, 11, 20, 160, W), i.mob(6, 18, .5), i.mob(26, 18, .5)),
    'nitrogen_shell_reinforce': lambda i: (i.shield(14, 16, 18, 21), i.shield(14, 16, 11, 13, W), i.line([(26, 6), (26, 12)], ALLY), i.line([(23, 9), (29, 9)], ALLY)),
    'nitrogen_item_push': lambda i: (i.shield(7, 15, 8, 10), i.item(17, 15), i.arrow(21, 15, 28, 15)),
    'nitrogen_shield_line': lambda i: (i.me(6, 10, s=.7), i.line([(14, 6), (14, 26)], W), i.line([(16, 6), (16, 26)], W), i.arrow(28, 10, 19, 10, RED), i.arrow(28, 21, 19, 21, RED)),
    'nitrogen_fall_guard': lambda i: (i.me(16, 4, s=.6), i.arrow(16, 13, 16, 19), i.shield(16, 22, 16, 6), i.ground(28)),
    'nitrogen_cone': lambda i: (i.shield(6, 16, 7, 9), i.line([(10, 16), (27, 6)], W), i.line([(10, 16), (27, 26)], W), i.burst(22, 16, 4)),
    'nitrogen_ally_guard': lambda i: (i.ally(10, 10), i.shield(22, 15, 8, 11), i.arrow(31, 15, 27, 15, RED)),
    'nitrogen_ground_wave': lambda i: (i.fist(16, 9), i.ground(22), i.arc(16, 22, 7, 180, 360, W), i.arc(16, 22, 12, 180, 360, W)),
    'nitrogen_dome': lambda i: (i.me(11, 13, s=.7), i.ally(20, 13, .7), i.dome(16, 26, 13, i.c)),
    'nitrogen_bulwark': lambda i: (i.ally(7, 10, .8), i.shield(18, 15, 10, 18, i.c), i.wall(23, 7, 24), i.arrow(31, 12, 27, 12, RED)),
    'nitrogen_shock_ring': lambda i: (i.me(16, 4, s=.6), i.circle(16, 20, 9, W), i.burst(16, 20, 4), i.mob(5, 22, .4), i.mob(27, 22, .4)),
    'nitrogen_rocket': lambda i: (i.me(19, 5, s=.7), i.arrow(10, 22, 19, 14), i.burst(9, 24, 4), i.ground(28)),
    'nitrogen_impact': lambda i: (i.shield(16, 8, 10, 11), i.arrow(16, 15, 16, 22), i.burst(16, 25, 5), i.ground(28)),
    # Aero Hand: airflow movement and pressure.
    'aero_jet': lambda i: (i.me(20, 8, s=.8), i.gust(3, 14, 2, W, 10), i.arrow(14, 24, 26, 18), i.ground(28)),
    'aero_brake': lambda i: (i.me(8, 9, s=.8), i.gust(16, 9, 3, W, 10), i.bar(14, 8, 26, W)),
    'aero_updraft': lambda i: (i.me(16, 4, s=.6), i.gust(9, 16, 2, W, 12), i.arrow(16, 26, 16, 14), i.ground(28)),
    'aero_push': lambda i: (i.gust(3, 9, 3, W, 11), i.mob(20, 9, .8), i.arrow(23, 24, 29, 24)),
    'aero_side_step': lambda i: (i.me(16, 8, s=.8), i.arrow(13, 25, 5, 25), i.arrow(19, 25, 27, 25)),
    'aero_backstep': lambda i: (i.me(20, 8, s=.8), i.arrow(15, 24, 5, 24), i.gust(22, 25, 1, W, 6)),
    'aero_fall_brake': lambda i: (i.me(16, 3, s=.6), i.arrow(16, 12, 16, 18), i.gust(8, 21, 2, W, 14)),
    'aero_item_pull': lambda i: (i.me(6, 10, s=.7), i.item(24, 18), i.arrow(20, 18, 12, 18), i.gust(14, 8, 1, W, 10)),
    'aero_gust': lambda i: (i.gust(4, 10, 3, W, 13), i.burst(24, 15, 4), i.mob(27, 7, .4)),
    'aero_launch': lambda i: (i.gust(3, 12, 2, W, 8), i.item(15, 15), i.arrow(19, 13, 28, 7)),
    'aero_cone': lambda i: (i.gust(3, 14, 1, W, 6), i.line([(10, 15), (27, 5)], W), i.line([(10, 15), (27, 25)], W), i.mob(22, 11, .45)),
    'aero_ring_push': lambda i: (i.me(16, 10, s=.8), i.circle(16, 16, 11, W), i.arrow(4, 16, 1, 16), i.arrow(28, 16, 31, 16)),
    'aero_vertical_jet': lambda i: (i.me(16, 9, s=.7), i.arrow(16, 30, 16, 25), i.line([(13, 30), (13, 25)], W), i.line([(19, 30), (19, 25)], W), i.arrow(16, 8, 16, 2)),
    'aero_arrow_deflect': lambda i: (i.gust(4, 11, 2, W, 10), i.arrow(26, 22, 18, 18, RED), i.arrow(18, 18, 24, 7, RED)),
    'aero_vortex': lambda i: (i.spiral(16, 16), i.item(25, 8, 2), i.item(7, 24, 2), i.item(25, 24, 2), i.item(7, 8, 2)),
    'aero_crosswind': lambda i: (i.mob(16, 8, .8), i.arrow(3, 25, 13, 25), i.arrow(19, 25, 29, 25), i.gust(4, 5, 1, W, 6)),
    'aero_pressure_wall': lambda i: (i.me(6, 10, s=.7), i.wall(16, 5, 26), i.gust(19, 9, 3, W, 7), i.arrow(30, 21, 22, 21, RED)),
    'aero_ground_blast': lambda i: (i.ground(25), i.arc(9, 25, 6, 180, 360, W), i.arc(18, 25, 6, 180, 360, W), i.arc(26, 25, 4, 180, 360, W), i.arrow(4, 15, 27, 15)),
    'aero_airborne_turn': lambda i: (i.me(8, 6, s=.6), i.arc(16, 22, 8, 180, 360, W), i.arrow(24, 21, 24, 26), i.hover(4, 28, 29)),
    'aero_barrage': lambda i: (i.gust(3, 6, 1, W, 8), i.gust(3, 15, 1, W, 8), i.gust(3, 24, 1, W, 8), i.arrow(14, 6, 26, 9), i.arrow(14, 15, 27, 15), i.arrow(14, 24, 26, 21)),
    'aero_squall': lambda i: (i.spiral(16, 16), i.mob(5, 6, .45), i.mob(27, 6, .45), i.mob(16, 23, .45)),
    'aero_air_curtain': lambda i: (i.me(16, 10, s=.8), i.dashed(16, 16, 12, W, 12), i.arrow(31, 5, 25, 9, RED)),
    'aero_double_jet': lambda i: (i.me(14, 4, s=.6), i.arrow(10, 20, 22, 20), i.arrow(10, 20, 10, 10), i.gust(4, 24, 1, W, 6)),
    'aero_pressure_lance': lambda i: (i.gust(2, 13, 1, W, 5), i.spear(8, 16, 29, 16), i.mob(22, 6, .5)),
    # Hydro Hand: water source + effect.
    'hydro_whip': lambda i: (i.drop(6, 10, .8, i.c), i.line([(9, 14), (14, 20), (20, 12), (24, 18)], W), i.mob(26, 7, .5)),
    'hydro_transfer': lambda i: (i.drop(7, 14, 1, i.c), i.arrow(12, 14, 21, 14), i.box(22, 10, 7, 8, W)),
    'hydro_extinguish': lambda i: (i.drop(9, 8, .7, i.c), i.flame(20, 19), i.cross(20, 18, 4, W)),
    'hydro_irrigate': lambda i: (i.drop(16, 7, .8, i.c), i.box(5, 18, 22, 8), i.line([(5, 22), (27, 22)], i.c), i.line([(12, 18), (12, 26)], i.c), i.line([(20, 18), (20, 26)], i.c)),
    'hydro_shield': lambda i: (i.me(8, 10, s=.8), i.drop(18, 14, 1.1, i.c), i.arrow(30, 14, 24, 14, RED)),
    'hydro_current': lambda i: (i.me(10, 6, s=.7), i.water(18), i.arrow(5, 24, 27, 24)),
    'hydro_repel': lambda i: (i.mob(16, 6, .7), i.water(18), i.arrow(10, 26, 4, 26), i.arrow(22, 26, 28, 26)),
    'hydro_lift': lambda i: (i.me(16, 4, s=.6), i.water(20), i.arrow(16, 27, 16, 14)),
    'hydro_brake': lambda i: (i.me(9, 5, s=.6), i.water(17), i.arrow(4, 25, 15, 25), i.bar(18, 21, 28)),
    'hydro_rescue': lambda i: (i.ally(16, 4, .6), i.water(18), i.arrow(10, 27, 10, 15), i.arrow(22, 27, 22, 15)),
    'hydro_domain': lambda i: (i.water(14), i.water(20), i.dashed(16, 17, 12, W), i.mob(16, 2, .45)),
    'hydro_douse_self': lambda i: (i.me(12, 9), i.flame(23, 18), i.drop(23, 6, .6, i.c)),
    'hydro_douse_ally': lambda i: (i.ally(9, 9), i.flame(22, 20), i.drop(22, 7, .6, i.c)),
    'hydro_douse_hostile': lambda i: (i.mob(9, 9), i.flame(22, 20), i.drop(22, 7, .6, i.c)),
    'hydro_campfire_out': lambda i: (i.flame(16, 15), i.line([(7, 24), (25, 20)], i.c), i.line([(7, 20), (25, 24)], i.c), i.drop(25, 6, .6, i.c)),
    'hydro_candle_out': lambda i: (i.box(13, 14, 6, 12, i.c), i.flame(16, 10), i.drop(25, 7, .6, i.c)),
    'hydro_pressure_jet': lambda i: (i.drop(5, 14, .7, i.c), i.line([(9, 14), (28, 14)], W), i.line([(9, 17), (28, 17)], W), i.arrow(24, 15, 29, 15)),
    'hydro_fan': lambda i: (i.drop(6, 16, .7, i.c), i.line([(9, 16), (27, 5)], W), i.line([(9, 16), (28, 16)], W), i.line([(9, 16), (27, 27)], W)),
    'hydro_eddy': lambda i: (i.water(24), i.spiral(16, 13), i.mob(4, 12, .4), i.mob(28, 12, .4)),
    'hydro_dive': lambda i: (i.water(9), i.me(16, 13, s=.6), i.arrow(16, 23, 16, 29)),
    'hydro_lateral_current': lambda i: (i.water(7), i.me(16, 12, s=.6), i.arrow(10, 26, 3, 26), i.arrow(22, 26, 29, 26)),
    'hydro_undertow': lambda i: (i.me(6, 8, s=.6), i.water(14), i.mob(25, 17, .5), i.arrow(21, 26, 10, 26)),
    'hydro_tow_ally': lambda i: (i.me(6, 8, s=.6), i.water(14), i.ally(23, 17, .5), i.arrow(20, 27, 10, 27)),
    'hydro_brake_field': lambda i: (i.water(18), i.me(11, 6, s=.5), i.ally(20, 6, .5), i.dashed(16, 16, 13, W), i.bar(15, 23, 28, W)),
    # Float Dial: buoyancy on self, allies, hostiles and items.
    'float_lift': lambda i: (i.mob(16, 10), i.arrow(16, 30, 16, 25, W), i.arrow(7, 22, 7, 12), i.arrow(25, 22, 25, 12)),
    'float_item_lift': lambda i: (i.item(16, 9), i.arrow(16, 25, 16, 15), i.ground(28)),
    'float_item_lower': lambda i: (i.item(16, 8), i.arrow(16, 14, 16, 24), i.ground(28)),
    'float_item_pull': lambda i: (i.me(6, 8, s=.7), i.item(24, 18), i.arrow(20, 18, 12, 18), i.hover(19, 29, 23)),
    'float_item_release': lambda i: (i.item(16, 10), i.hover(8, 24, 16), i.cross(16, 22, 3, W)),
    'float_rescue': lambda i: (i.ally(16, 4, .7), i.arrow(16, 15, 16, 20), i.hover(6, 26, 23), i.arc(16, 26, 6, 200, 340, W)),
    'float_item_push': lambda i: (i.me(6, 8, s=.7), i.item(16, 18), i.arrow(20, 18, 28, 18)),
    'float_field': lambda i: (i.mob(9, 10, .6), i.mob(23, 10, .6), i.dashed(16, 16, 13, W), i.arrow(16, 28, 16, 20)),
    'float_item_field': lambda i: (i.item(8, 9, 2), i.item(24, 9, 2), i.item(8, 21, 2), i.item(24, 21, 2), i.arrow(16, 22, 16, 10)),
    'float_release_field': lambda i: (i.item(8, 9, 2), i.item(24, 9, 2), i.item(16, 19, 2), i.dashed(16, 15, 12, W), i.cross(16, 6, 2, W)),
    'float_hover': lambda i: (i.me(16, 6, s=.8), i.hover(6, 26, 21), i.ground(28)),
    'float_ascent': lambda i: (i.me(12, 10, s=.8), i.arrow(24, 26, 24, 8), i.dashed(24, 17, 4, W, 4)),
    'float_descent': lambda i: (i.me(12, 6, s=.8), i.arrow(24, 8, 24, 24), i.ground(28)),
    'float_air_brake': lambda i: (i.me(16, 4, s=.6), i.hover(5, 27, 15), i.arrow(5, 23, 13, 23), i.bar(16, 19, 27, W)),
    'float_ally_hover': lambda i: (i.ally(14, 6, .8), i.hover(5, 25, 21)),
    'float_ally_ascent': lambda i: (i.ally(11, 10, .8), i.arrow(25, 26, 25, 8)),
    'float_ally_descent': lambda i: (i.ally(11, 6, .8), i.arrow(25, 8, 25, 24), i.ground(28)),
    'float_suspend': lambda i: (i.mob(16, 5, .8), i.hover(6, 26, 20), i.bar(15, 22, 27, W)),
    'float_hostile_descent': lambda i: (i.mob(11, 5, .8), i.arrow(24, 6, 24, 23), i.hover(18, 30, 25)),
    'float_hover_field': lambda i: (i.mob(9, 6, .6), i.mob(23, 6, .6), i.hover(4, 28, 19), i.dashed(16, 16, 13, W)),
    'float_rescue_field': lambda i: (i.me(10, 4, s=.55), i.ally(21, 4, .55), i.hover(4, 28, 18), i.arc(16, 26, 9, 200, 340, W)),
    'float_item_shelf': lambda i: (i.item(16, 11), i.hover(8, 24, 16), i.ground(27)),
    'float_item_orbit': lambda i: (i.me(16, 10, s=.7), i.circle(16, 16, 11, W), i.item(27, 16, 2)),
    'float_item_shelf_field': lambda i: (i.item(7, 11, 2), i.item(16, 11, 2), i.item(25, 11, 2), i.hover(3, 29, 16), i.ground(27)),
    # Liquid Shadow: the remote liquid body.
    'liquid_scout': lambda i: (i.blob(16, 20), i.eye(16, 8)),
    'liquid_pause': lambda i: (i.blob(12, 20), i.bar(21, 8, 16, W), i.bar(25, 8, 16, W)),
    'liquid_recall': lambda i: (i.me(6, 8, s=.7), i.blob(24, 22), i.arrow(17, 14, 10, 14)),
    'liquid_dismiss': lambda i: (i.blob(16, 20), i.cross(16, 8, 4, W)),
    'liquid_turn': lambda i: (i.blob(16, 22), i.arc(16, 11, 7, 180, 450, W), i.arrow(17, 4, 21, 5)),
    'liquid_retreat': lambda i: (i.blob(20, 20), i.arrow(10, 12, 3, 12), i.me(26, 4, s=.4)),
    'liquid_guard': lambda i: (i.blob(9, 20), i.line([(17, 8), (20, 12), (20, 24)], W), i.arrow(30, 16, 23, 16, RED)),
    'liquid_listen': lambda i: (i.blob(9, 23), i.ear(18, 9), i.sound(26, 9)),
    'liquid_path': lambda i: (i.blob(7, 23), i.path([(12, 23), (18, 23), (18, 15), (27, 15)]), i.box(22, 18, 5, 8, i.c)),
    'liquid_strike': lambda i: (i.blob(9, 21), i.arrow(15, 16, 22, 11), i.burst(24, 10, 4), i.mob(26, 15, .45)),
    'liquid_push': lambda i: (i.blob(8, 21), i.mob(20, 8, .7), i.arrow(22, 25, 29, 25)),
    'liquid_intercept': lambda i: (i.blob(9, 21), i.arrow(28, 6, 18, 14, RED), i.cross(18, 13, 3, W)),
    'liquid_switch': lambda i: (i.blob(9, 21), i.box(20, 18, 7, 5, W), i.line([(23, 18), (27, 10)], W), i.circle(27, 10, 1, RED)),
    'liquid_follow': lambda i: (i.me(22, 7, s=.7), i.blob(9, 22), i.path([(14, 18), (19, 18)]), i.arrow(17, 18, 20, 18)),
    'liquid_route': lambda i: (i.blob(6, 24), i.path([(11, 24), (17, 17), (25, 17)]), i.target(25, 10, 3)),
    'liquid_screen': lambda i: (i.blob(9, 21), i.wall(18, 5, 26), i.arrow(30, 7, 22, 7, RED), i.arrow(30, 15, 22, 15, RED), i.arrow(30, 23, 22, 23, RED)),
    'liquid_survey': lambda i: (i.blob(9, 22), i.circle(22, 15, 6, W), i.dot(22, 15, RED), i.path([(14, 19), (18, 17)])),
    'liquid_pulse': lambda i: (i.blob(16, 18), i.arc(16, 18, 9, 180, 360, W), i.arc(16, 18, 13, 200, 340, W), i.arrow(4, 26, 1, 28), i.arrow(28, 26, 31, 28)),
    'liquid_shadow_field': lambda i: (i.blob(16, 18), i.dashed(16, 16, 13, W), i.mob(5, 6, .4), i.mob(27, 6, .4)),
    'liquid_dual_sensor': lambda i: (i.blob(16, 24), i.ear(9, 10), i.circle(24, 10, 4, W), i.dot(24, 10, RED)),
    'liquid_grapple': lambda i: (i.blob(8, 22), i.line([(13, 19), (17, 14), (21, 14)], W), i.mob(24, 8, .6), i.bar(28, 9, 18, W)),
    'liquid_contact_cut': lambda i: (i.blob(8, 22), i.blade(15, 21, 27, 9), i.mob(25, 18, .4)),
    'liquid_contact_sweep': lambda i: (i.blob(16, 24), i.arc(16, 18, 11, 200, 340, W), i.mob(6, 7, .4), i.mob(16, 4, .4), i.mob(26, 7, .4)),
    'liquid_contact_crush': lambda i: (i.blob(16, 25), i.mob(16, 6, .6), i.arrow(5, 13, 11, 13), i.arrow(27, 13, 21, 13)),
    # Paper Psychokinesis: sheets, constructs and redstone fixtures.
    'paper_blade': lambda i: (i.sheet(4, 12, 6, 8), i.blade(12, 24, 27, 9)),
    'paper_patch': lambda i: (i.sheet(4, 6, 5, 7), i.sheet(8, 8, 5, 7), i.box(17, 15, 9, 9, W)),
    'paper_retract': lambda i: (i.box(8, 14, 9, 9, i.c), i.arrow(20, 18, 28, 18), i.cross(12, 18, 3, W)),
    'paper_plane': lambda i: (i.plane(14, 15), i.path([(22, 12), (28, 9)])),
    'paper_walkway': lambda i: (i.blocks(4, 18, 3, 8, W), i.me(16, 2, s=.55)),
    'paper_step': lambda i: (i.steps(5, 26, 3, True, W), i.sheet(22, 4, 5, 7)),
    'paper_ramp': lambda i: (i.steps(4, 27, 4, True, W), i.line([(4, 27), (24, 11)], i.c)),
    'paper_pad': lambda i: (i.blocks(4, 18, 4, 6, W), i.blocks(4, 24, 4, 6, i.c)),
    'paper_screen': lambda i: (i.blocks(14, 22, 3, 6, W, True), i.arrow(28, 14, 22, 14, RED), i.me(5, 10, s=.6)),
    'paper_armor': lambda i: (i.me(11, 8), i.sheet(18, 6, 5, 7), i.sheet(20, 13, 5, 7), i.arrow(31, 25, 24, 22, RED)),
    'paper_wing': lambda i: (i.me(16, 6, s=.6), i.plane(9, 18), i.plane(23, 18), i.arrow(4, 28, 28, 24)),
    'paper_switch': lambda i: (i.box(6, 10, 12, 12, W), i.button(20, 14), i.line([(23, 15), (27, 15)], RED)),
    'paper_plate': lambda i: (i.box(6, 14, 18, 10, W), i.plate(9, 12, 12), i.arrow(15, 4, 15, 10)),
    'paper_lift_bridge': lambda i: (i.line([(3, 22), (10, 22), (13, 17), (19, 17), (22, 22), (29, 22)], W), i.line([(3, 25), (29, 25)], i.c)),
    'paper_cover': lambda i: (i.blocks(6, 20, 3, 7, W), i.blocks(6, 13, 3, 7, W), i.arrow(30, 6, 25, 9, RED)),
    'paper_gate': lambda i: (i.box(4, 12, 6, 14, i.c), i.gate(13, 15), i.arc(22, 26, 7, 270, 360, W)),
    'paper_platform': lambda i: (i.poly([(4, 18), (16, 12), (28, 18), (16, 24)], W), i.line([(10, 15), (22, 21)], W), i.line([(10, 21), (22, 15)], W), i.line([(4, 18), (4, 22), (16, 28), (28, 22), (28, 18)], i.c)),
    'paper_puzzle_link': lambda i: (i.box(4, 16, 9, 9, W), i.plate(5, 14, 7), i.link(14, 20, 23, 20), i.circle(26, 20, 3, RED)),
    'paper_safety_rail': lambda i: (i.ground(26), i.line([(4, 14), (28, 14)], W), i.line([(6, 14), (6, 26)], W), i.line([(16, 14), (16, 26)], W), i.line([(26, 14), (26, 26)], W)),
    'paper_ladder': lambda i: (i.box(4, 6, 6, 21, i.c), i.ladder(14, 6, 27)),
    'paper_array': lambda i: (i.sheet(13, 4, 6, 8), i.blade(6, 26, 13, 17), i.blade(20, 26, 27, 17), i.arrow(16, 14, 16, 26)),
    'paper_catwalk': lambda i: (i.blocks(2, 16, 5, 5, W), i.line([(2, 13), (27, 13)], i.c), i.me(16, 1, s=.45)),
    'paper_switch_bridge': lambda i: (i.button(4, 10), i.link(8, 12, 14, 16), i.blocks(14, 18, 3, 5, W), i.gate(15, 6)),
    'paper_emergency_stairs': lambda i: (i.steps(4, 9, 4, False, W), i.me(7, 0, s=.45), i.arrow(26, 10, 26, 26)),
}


def main():
    skills = json.loads((ROOT / 'docs/canon/skills.json').read_text())
    missing = [s['id'] for s in skills if s['id'] not in RECIPES]
    if missing: raise SystemExit('No recipe for: ' + ', '.join(missing))
    seen = {}
    for s in skills:
        icon = Icon(s['category'], s['level']); RECIPES[s['id']](icon)
        # The atlas contract keeps a fully transparent 1px border; tier marks are redrawn so glyphs never cover them.
        px = icon.im.load()
        for k in range(32):
            for x, y in ((k, 0), (k, 31), (0, k), (31, k)): px[x, y] = (0, 0, 0, 0)
        if s['level'] >= 4: icon.line([(9, 1), (13, 1), (15, 3), (17, 1), (22, 1)], GOLD)
        if s['level'] >= 5: icon.line([(10, 30), (14, 30), (16, 28), (18, 30), (22, 30)], GOLD)
        digest = hashlib.sha256(icon.im.tobytes()).hexdigest()
        key = (s['category'], digest)
        if key in seen: raise SystemExit(f"{s['id']} renders identically to {seen[key]}")
        seen[key] = s['id']
        for directory in ('skill_icons_v2', 'abilities'):
            folder = ASSETS / directory / s['category']; folder.mkdir(parents=True, exist_ok=True)
            icon.im.save(folder / (s['id'] + '.png'))
    manifest_path = ROOT / 'src/main/resources/skill-icons-v2.json'
    manifest = json.loads(manifest_path.read_text()); by_id = {s['id']: s for s in skills}
    for entry in manifest['icons']:
        if entry['category'] in COLORS and entry['id'] in by_id:
            path = ASSETS / 'skill_icons_v2' / entry['category'] / (entry['id'] + '.png')
            entry['sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
            entry['meaning'] = by_id[entry['id']]['en']['description'] + ' Per-skill pictogram: affected subject plus action.'
            entry['generator'] = 'scripts/generate-canon-skill-icons.py'
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    print('Canon icons written:', len(skills))


if __name__ == '__main__':
    main()
