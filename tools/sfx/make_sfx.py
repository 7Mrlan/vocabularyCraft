#!/usr/bin/env python3
"""Generate the app's review sounds.

The palette is paper, wood and felt, not arcade: low partials, short decays,
a quiet master, and no bright high-frequency sparkle. Everything is synthesized
here so the repository carries no third-party audio.

Run:  python3 tools/sfx/make_sfx.py
"""

from __future__ import annotations

import math
import random
import struct
import wave
from pathlib import Path

SR = 22050
OUT = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res" / "raw"

# Soft struck wood: enough body to read as an object, no bell-like top end.
WOOD = ((1.0, 1.0, 18.0), (2.01, 0.18, 26.0), (3.8, 0.06, 34.0))
# Felt: darker and shorter than wood.
FELT = ((1.0, 1.0, 24.0), (1.51, 0.12, 32.0))
# A quiet glass edge, used only for the two "got it" cues.
GLASS = ((1.0, 1.0, 10.0), (2.01, 0.20, 14.0), (3.02, 0.05, 22.0))


def tone(freq: float, dur: float, amp: float, partials, attack: float = 0.006) -> list[float]:
    """Sum decaying partials into one soft struck-note sample."""
    n = int(dur * SR)
    out = [0.0] * n
    for ratio, weight, decay in partials:
        f = freq * ratio
        if f >= SR / 2:
            continue
        for i in range(n):
            t = i / SR
            env = math.exp(-decay * t) * (1 - math.exp(-t / attack))
            out[i] += amp * weight * env * math.sin(2 * math.pi * f * t + ratio)
    return out


def soft_tick(dur: float, amp: float, cutoff: float) -> list[float]:
    """A pencil tip on paper: filtered noise with almost no pitch."""
    n = int(dur * SR)
    out = [0.0] * n
    rnd = random.Random(11)
    y = 0.0
    alpha = min(1.0, 2 * math.pi * cutoff / SR)
    for i in range(n):
        x = rnd.uniform(-1.0, 1.0)
        y += alpha * (x - y)
        t = i / SR
        env = math.exp(-42.0 * t)
        out[i] = amp * env * y
    return out


def mix(*parts: tuple[list[float], float]) -> list[float]:
    """Mix offset samples, then normalize and fade the result."""
    length = max(int(offset * SR) + len(samples) for samples, offset in parts)
    out = [0.0] * length
    for samples, offset in parts:
        start = int(offset * SR)
        for i, value in enumerate(samples):
            out[start + i] += value
    return master(out)


def master(samples: list[float], peak: float = 0.50) -> list[float]:
    """Normalize to a quiet peak and fade the edges; no limiter artefacts."""
    top = max(abs(v) for v in samples) or 1.0
    scale = peak / top
    out = [v * scale for v in samples]
    fade = min(int(0.004 * SR), len(out) // 3)
    for i in range(fade):
        gain = i / fade
        out[i] *= gain
        out[-1 - i] *= gain
    return out


def write(name: str, samples: list[float]) -> None:
    path = OUT / f"{name}.wav"
    with wave.open(str(path), "wb") as fh:
        fh.setnchannels(1)
        fh.setsampwidth(2)
        fh.setframerate(SR)
        fh.writeframes(b"".join(struct.pack("<h", int(v * 32767)) for v in samples))
    print(f"  {name}.wav  {len(samples) / SR * 1000:.0f} ms  {path.stat().st_size} bytes")


def main() -> None:
    """Write every review cue into the app's raw resource directory."""
    OUT.mkdir(parents=True, exist_ok=True)
    print("Writing review sounds")

    # 秒懂 — two warm wood notes a fifth apart, short and low.
    write("grade_easy", mix((tone(523.25, 0.24, 0.52, WOOD), 0.0),
                            (tone(783.99, 0.30, 0.44, WOOD), 0.055)))

    # 认识 — one soft wood note, no fanfare.
    write("grade_good", mix((tone(392.00, 0.20, 0.46, WOOD), 0.0),))

    # 模糊 — a darker felt note, shorter than "认识".
    write("grade_hard", mix((tone(329.63, 0.17, 0.40, FELT), 0.0),))

    # 忘记 — a muffled tick; the answer ends, nothing scolds.
    write("grade_again", soft_tick(0.075, 0.34, 1100.0))

    # 拼对 — two low wood notes, not a major arpeggio.
    write("spell_right", mix((tone(392.00, 0.18, 0.44, WOOD), 0.0),
                             (tone(493.88, 0.24, 0.40, WOOD), 0.075)))

    # 拼错 — the same tick, even softer.
    write("spell_wrong", soft_tick(0.075, 0.26, 950.0))

    # 四选一答对 — one soft wood note; the old chord was too celebratory.
    write("quiz_correct", mix((tone(523.25, 0.18, 0.42, WOOD), 0.0),))

    # 书架过卡 — a paper tick, off by default in the app.
    write("gear_click", soft_tick(0.050, 0.22, 1600.0))


if __name__ == "__main__":
    main()
