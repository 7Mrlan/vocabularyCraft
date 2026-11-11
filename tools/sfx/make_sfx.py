#!/usr/bin/env python3
"""Generate the app's review sounds.

The palette is paper-and-ink, not arcade: soft attacks, short decays, and
partials that sound like a struck glass or a wooden desk rather than a beep.
Everything is synthesized here so the repository carries no third-party audio.

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

# (frequency, amplitude, decay) partials for a struck-glass timbre
GLASS = ((1.0, 1.0, 7.0), (2.01, 0.42, 9.0), (3.02, 0.18, 12.0), (4.2, 0.08, 16.0))
WOOD = ((1.0, 1.0, 26.0), (2.7, 0.30, 34.0), (5.1, 0.12, 40.0))
LOW = ((1.0, 1.0, 9.0), (1.98, 0.25, 12.0), (0.5, 0.35, 6.0))


def tone(freq: float, dur: float, amp: float, partials, attack: float = 0.004) -> list[float]:
    n = int(dur * SR)
    out = [0.0] * n
    for ratio, weight, decay in partials:
        f = freq * ratio
        if f >= SR / 2:
            continue
        for i in range(n):
            t = i / SR
            env = math.exp(-decay * t) * (1 - math.exp(-t / attack if attack > 0 else 1))
            out[i] += amp * weight * env * math.sin(2 * math.pi * f * t + ratio)
    return out


def swish(dur: float, amp: float, cutoff: float = 3400.0) -> list[float]:
    """A short filtered-noise page turn."""
    n = int(dur * SR)
    out = [0.0] * n
    rnd = random.Random(7)
    y = 0.0
    alpha = min(1.0, 2 * math.pi * cutoff / SR)
    for i in range(n):
        x = rnd.uniform(-1.0, 1.0)
        y += alpha * (x - y)
        t = i / SR
        env = math.sin(math.pi * min(1.0, t / dur)) ** 2
        out[i] = amp * env * y
    return out


def mix(*parts: tuple[list[float], float]) -> list[float]:
    length = max(int(offset * SR) + len(samples) for samples, offset in parts)
    out = [0.0] * length
    for samples, offset in parts:
        start = int(offset * SR)
        for i, value in enumerate(samples):
            out[start + i] += value
    peak = max(abs(v) for v in out) or 1.0
    scale = 0.72 / peak
    return [v * scale for v in out]


def write(name: str, samples: list[float]) -> None:
    path = OUT / f"{name}.wav"
    with wave.open(str(path), "wb") as fh:
        fh.setnchannels(1)
        fh.setsampwidth(2)
        fh.setframerate(SR)
        fh.writeframes(b"".join(struct.pack("<h", int(v * 32767)) for v in samples))
    print(f"  {name}.wav  {len(samples) / SR * 1000:.0f} ms  {path.stat().st_size} bytes")


def tick(dur: float, amp: float) -> list[float]:
    """A dry, neutral click — a pencil tip on paper, never a buzzer."""
    n = int(dur * SR)
    out = [0.0] * n
    rnd = random.Random(11)
    y = 0.0
    alpha = min(1.0, 2 * math.pi * 2600.0 / SR)
    for i in range(n):
        x = rnd.uniform(-1.0, 1.0)
        y += alpha * (x - y)
        t = i / SR
        env = math.exp(-34.0 * t)
        out[i] = amp * env * (y + 0.55 * math.sin(2 * math.pi * 210.0 * t))
    return out


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    print("Writing review sounds")

    # 秒懂 — a bright fifth, A5 up to E6: the clearest "got it".
    write("grade_easy", mix((tone(880.0, 0.30, 0.55, GLASS), 0.0),
                            (tone(1318.5, 0.40, 0.50, GLASS), 0.075),
                            (tone(2637.0, 0.16, 0.10, GLASS), 0.075)))

    # 认识 — the same voice a third lower and shorter: right, but no fanfare.
    write("grade_good", mix((tone(523.25, 0.24, 0.48, GLASS), 0.0),
                            (tone(659.25, 0.30, 0.42, GLASS), 0.065)))

    # 模糊 — one soft note with no lift: neither praise nor punishment.
    write("grade_hard", mix((tone(440.0, 0.26, 0.42, GLASS), 0.0),))

    # 忘记 — a dry tick, flat in pitch: the answer ends, nothing scolds.
    write("grade_again", tick(0.09, 0.55))

    # 拼对 — the reward: a bright major arpeggio with a shimmer tail.
    write("spell_right", mix((tone(1046.5, 0.30, 0.50, GLASS), 0.0),
                             (tone(1318.5, 0.32, 0.48, GLASS), 0.085),
                             (tone(1568.0, 0.46, 0.52, GLASS), 0.17),
                             (tone(3136.0, 0.18, 0.10, GLASS), 0.17)))

    # 拼错 — the same dry tick, softer.
    write("spell_wrong", tick(0.09, 0.40))


if __name__ == "__main__":
    main()
