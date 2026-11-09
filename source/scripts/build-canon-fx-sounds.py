#!/usr/bin/env python3
"""Synthesise the original canon skill sounds (no samples). Run with a Python that has numpy + soundfile:
   python3 scripts/build-canon-fx-sounds.py
Writes ogg files under assets/academy/sounds/canon and merges canon.* events into sounds.json."""
import json
from pathlib import Path

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/academy"
OUT = ROOT / "sounds/canon"
OUT.mkdir(parents=True, exist_ok=True)
SR = 44100
rng = np.random.default_rng(1028)


def t_(d): return np.arange(int(SR * d)) / SR
def env(n, a=.005, r=.2, curve=2.0):
    x = np.arange(n) / SR
    atk = np.clip(x / max(a, 1e-4), 0, 1)
    rel = np.clip((n / SR - x) / max(r, 1e-4), 0, 1) ** curve
    return atk * rel
def noise(n): return rng.standard_normal(n)
def lowpass(x, fc):
    fc = np.broadcast_to(fc, x.shape); y = np.zeros_like(x); s = 0.0
    for i in range(len(x)):
        a = np.exp(-2 * np.pi * fc[i] / SR); s = (1 - a) * x[i] + a * s; y[i] = s
    return y
def highpass(x, fc): return x - lowpass(x, fc)
def bandpass_sweep(n, f0, f1, q=6.0):
    """State-variable filter whose centre moves f0 -> f1; used for whooshes."""
    x = noise(n); low = band = 0.0; out = np.zeros(n)
    for i in range(n):
        f = f0 + (f1 - f0) * i / n; g = 2 * np.sin(np.pi * f / SR)
        low += g * band; high = x[i] - low - band / q; band += g * high; out[i] = band
    return out
def sine(f, d, phase=0): return np.sin(2 * np.pi * np.cumsum(np.broadcast_to(f, (int(SR * d),)).astype(float)) / SR + phase)
def norm(x, peak=.8): return x / (np.max(np.abs(x)) + 1e-9) * peak
def save(name, x):
    x = norm(x); fade = int(SR * .004); x[-fade:] *= np.linspace(1, 0, fade)
    sf.write(OUT / f"{name}.ogg", x.astype(np.float32), SR, format="OGG", subtype="VORBIS")


def mental(hit):
    d = .9 if hit else 1.1; t = t_(d); n = len(t); sig = np.zeros(n)
    for k, f in enumerate([1318, 1760, 2349, 3136, 3951]):
        vib = 1 + .004 * np.sin(2 * np.pi * (5 + k) * t)
        sig += sine(f * vib, d) * np.exp(-t * (3 + k * 1.3)) * (.6 / (k + 1))
    sig += highpass(noise(n), 4000) * np.exp(-t * 14) * .25
    if hit: sig += sine(880 * np.exp(-t * 3), d) * np.exp(-t * 6) * .5
    return sig * env(n, .003, .25)
def dark(hit):
    d = .8 if hit else 1.3; n = int(SR * d); t = t_(d)
    sw = bandpass_sweep(n, 400, 2600 if not hit else 1800, 5)
    shimmer = sum(sine(f * (1 + .01 * np.sin(2 * np.pi * (3 + i) * t)), d) * (.3 / (i + 1)) for i, f in enumerate([523, 784, 1046, 1568]))
    return (norm(sw, .6) + shimmer * (np.exp(-t * 2) if hit else np.sin(np.pi * np.clip(t / d, 0, 1)))) * env(n, .02, .35)
def nitrogen(hit):
    d = .55 if hit else .9; n = int(SR * d); t = t_(d)
    thump = sine(150 * np.exp(-t * 9) + 35, d) * np.exp(-t * 8) * (1.0 if hit else .5)
    frost = highpass(noise(n), 5000) * np.exp(-t * (7 if hit else 3))
    crack = lowpass(noise(n), 900) * np.exp(-t * 30) * .7
    return (thump + frost * .5 + crack) * env(n, .001, .15)
def aero(hit):
    d = .6 if hit else 1.0; n = int(SR * d)
    w = bandpass_sweep(n, 700, 4200, 3) if hit else bandpass_sweep(n, 300, 2200, 3)
    t = t_(d); return norm(w) * (np.exp(-t * 3.5) if hit else np.sin(np.pi * np.clip(t / d, 0, 1)) ** .6) * env(n, .01, .2)
def hydro(hit):
    d = .7 if hit else 1.0; n = int(SR * d); t = t_(d)
    flow = lowpass(highpass(noise(n), 400), 3500) * (np.exp(-t * 5) if hit else .5 + .5 * np.sin(2 * np.pi * 3 * t))
    out = flow * .6
    for _ in range(14 if hit else 9):
        st = rng.uniform(0, d * .8); f0 = rng.uniform(500, 1500); ln = int(SR * rng.uniform(.03, .08)); i0 = int(st * SR)
        tt = np.arange(ln) / SR; bub = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 8 * tt)) / SR) * np.exp(-tt * 55)
        out[i0:i0 + ln] += bub[:max(0, min(ln, n - i0))] * .5
    return out * env(n, .005, .25)
def float_(hit):
    d = .75 if hit else 1.2; n = int(SR * d); out = np.zeros(n)
    for k in range(8 if hit else 6):
        st = rng.uniform(0, d * .75) if not hit else k * .05; f0 = rng.uniform(600, 1100) * (1 + k * .03)
        ln = int(SR * .09); i0 = int(st * SR); tt = np.arange(ln) / SR
        pop = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 6 * tt)) / SR) * np.exp(-tt * 40)
        out[i0:i0 + ln] += pop[:max(0, min(ln, n - i0))]
    t = t_(d); out += sine(180 + 60 * t / d, d) * .12 * np.exp(-t * 2)
    return out * env(n, .003, .3)
def liquid(hit):
    d = .75 if hit else 1.1; n = int(SR * d); t = t_(d)
    squelch = lowpass(noise(n), 300 + 600 * np.abs(np.sin(2 * np.pi * 4 * t))) * np.exp(-t * (4 if hit else 1.8))
    gloop = sine(220 * np.exp(-t * 5) + 60, d) * np.exp(-t * 5)
    drip = np.zeros(n)
    for k in range(3):
        i0 = int(SR * (.15 + .22 * k)); ln = int(SR * .12); tt = np.arange(ln) / SR
        drip[i0:i0 + ln] += (np.sin(2 * np.pi * np.cumsum(400 * np.exp(-tt * 12) + 120) / SR) * np.exp(-tt * 25))[:max(0, min(ln, n - i0))]
    return (squelch * 1.4 + gloop * (1.2 if hit else .5) + drip * .5) * env(n, .004, .3)
def paper(hit):
    d = .55 if hit else .9; n = int(SR * d); t = t_(d)
    crinkle = np.zeros(n)
    for _ in range(40 if hit else 30):
        st = int(rng.uniform(0, n * .85)); ln = int(SR * rng.uniform(.002, .012))
        crinkle[st:st + ln] += highpass(noise(ln), 2500) * rng.uniform(.3, 1)
    flap = bandpass_sweep(n, 1500, 3500, 2) * np.exp(-t * 6) * .8
    return (crinkle + flap * 1.5) * env(n, .002, .2)


MAKERS = {"mental": mental, "dark": dark, "nitrogen": nitrogen, "aero": aero, "hydro": hydro,
          "float": float_, "liquid": liquid, "paper": paper}
events = {}
for key, fn in MAKERS.items():
    for phase, hit in (("start", False), ("hit", True)):
        save(f"{key}_{phase}", fn(hit))
        events[f"canon.{key}.{phase}"] = {"sounds": [{"name": f"academy:canon/{key}_{phase}", "stream": False}]}

path = ROOT / "sounds.json"
data = json.loads(path.read_text(encoding="utf-8"))
data.update(events)
path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print("wrote", len(events), "sound events")
