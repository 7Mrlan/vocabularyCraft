#!/usr/bin/env python3
"""Build the three word books from public lists.

Only headwords are kept — no definitions, translations or IPA from the source
files. The output is committed under app/src/main/assets/wordbooks/ so the app
never needs the network to know what is in a book.

Sources (see README.md beside this script):
  NGSL 1.2        CC BY-SA 4.0, Browne/Culligan/Phillips
  CET-4/6 lists   widely circulated syllabus lists (headwords only)
  NPEE list       postgraduate entrance exam syllabus (headwords only)
  COCA 20000      frequency ranking used to order every book

Run:  python3 tools/wordbooks/build_wordbooks.py
"""

from __future__ import annotations

import csv
import io
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / ".cache"
OUT = ROOT / "app" / "src" / "main" / "assets" / "wordbooks"

SOURCES = {
    "ngsl": "https://www.newgeneralservicelist.com/s/NGSL_12_stats.csv",
    "cet4": "https://raw.githubusercontent.com/mahavivo/english-wordlists/master/CET4_edited.txt",
    "cet6": "https://raw.githubusercontent.com/mahavivo/english-wordlists/master/CET6_edited.txt",
    "kaoyan": "https://raw.githubusercontent.com/mahavivo/english-wordlists/master/NPEE_Wordlist.txt",
    "coca": "https://raw.githubusercontent.com/mahavivo/english-wordlists/master/COCA_20000.txt",
}

WORD = re.compile(r"^[a-z][a-z'-]*$")
TARGETS = {"core3000": 3000, "cet2500": 2500, "kaoyan3000": 3000}

# Articles, pronouns, prepositions, conjunctions, auxiliaries, determiners and
# question words. They sit at the top of every frequency list and are worthless
# as study cards for anyone past beginner, so they never enter a book.
FUNCTION_WORDS = set("""
a an the
i me my mine myself you your yours yourself yourselves he him his himself
she her hers herself it its itself we us our ours ourselves they them their
theirs themselves who whom whose which what that this these those
someone somebody something anyone anybody anything everyone everybody
everything nobody nothing none one
of in to for with on at by from up about into over after beneath under above
across along among around before behind below beside between beyond during
except inside outside since through throughout toward towards until upon
within without off down out per via despite against near past round till
and but or nor so yet because although though if unless while whereas whether
either neither both than as
be am is are was were been being
have has had having
do does did done doing
will would shall should can could may might must ought
some any no every each all few many much more most several such another other
others own same
not
there here when where why how
""".split())


def fetch(name: str) -> str:
    CACHE.mkdir(parents=True, exist_ok=True)
    cached = CACHE / name
    if cached.exists() and cached.stat().st_size > 0:
        return cached.read_text(encoding="utf-8", errors="replace")
    print(f"  fetching {SOURCES[name]}")
    with urllib.request.urlopen(SOURCES[name], timeout=120) as response:
        data = response.read().decode("utf-8-sig", errors="replace")
    cached.write_text(data, encoding="utf-8")
    return data


def headword(line: str) -> str:
    """The first token of a syllabus line, e.g. 'abandon [ə... vt.离开' → abandon."""
    token = line.strip().lstrip("﻿").split(" ")[0].strip("()[]{}.,;:·—-")
    token = token.lower()
    return token if WORD.match(token) else ""


def from_ngsl(text: str) -> list[str]:
    rows = csv.reader(io.StringIO(text))
    next(rows, None)
    words = [row[0].strip().lower() for row in rows if row and row[0].strip()]
    return [w for w in words if WORD.match(w)]


def from_lines(text: str) -> list[str]:
    seen: list[str] = []
    for line in text.splitlines():
        word = headword(line)
        if word and word not in seen:
            seen.append(word)
    return seen


def from_coca(text: str) -> list[str]:
    return [w for w in (line.strip().lower() for line in text.splitlines()) if WORD.match(w)]


def order_by_frequency(words: list[str], rank: dict[str, int]) -> list[str]:
    """Known words first by COCA rank; unknown words keep their source order."""
    indexed = {word: i for i, word in enumerate(words)}
    return sorted(words, key=lambda w: (rank.get(w, len(rank) + 1), indexed[w]))


def main() -> int:
    print("Reading sources")
    ngsl = from_ngsl(fetch("ngsl"))
    cet = from_lines(fetch("cet4")) + from_lines(fetch("cet6"))
    kaoyan = from_lines(fetch("kaoyan"))
    coca = from_coca(fetch("coca"))
    rank = {word: i for i, word in enumerate(coca)}

    def content(words: list[str]) -> list[str]:
        """Headwords minus function words, in source order."""
        return [w for w in dict.fromkeys(words) if w not in FUNCTION_WORDS]

    # Core: NGSL is 2809 lemmas; top up from COCA so the book keeps its 3000-word promise.
    core = content(ngsl)
    seen = set(core)
    for word in coca:
        if len(core) >= TARGETS["core3000"]:
            break
        if word not in seen and word not in FUNCTION_WORDS:
            core.append(word)
            seen.add(word)

    cet_unique = content(cet)
    cet_ordered = order_by_frequency(cet_unique, rank)[: TARGETS["cet2500"]]

    kaoyan_unique = content(kaoyan)
    kaoyan_ordered = order_by_frequency(kaoyan_unique, rank)[: TARGETS["kaoyan3000"]]

    books = {
        "core3000": core[: TARGETS["core3000"]],
        "cet2500": cet_ordered,
        "kaoyan3000": kaoyan_ordered,
    }

    OUT.mkdir(parents=True, exist_ok=True)
    for name, words in books.items():
        path = OUT / f"{name}.txt"
        path.write_text("\n".join(words) + "\n", encoding="utf-8")
        missing = sum(1 for w in words if w not in rank)
        print(f"  {name}: {len(words)} words → {path.relative_to(ROOT)} "
              f"({missing} without a COCA rank)")

    core_set = set(books["core3000"])
    print("\nCoverage of source lists")
    print(f"  NGSL lemmas         {len(dict.fromkeys(ngsl))} "
          f"({len(dict.fromkeys(ngsl)) - len(content(ngsl))} function words dropped)")
    print(f"  CET-4 ∪ CET-6       {len(cet_unique)}")
    print(f"  考研大纲             {len(kaoyan_unique)}")
    print(f"  core ∩ cet          {len(core_set & set(cet_unique))}")
    print(f"  core ∩ 考研          {len(core_set & set(kaoyan_unique))}")
    print(f"  cet ∩ 考研           {len(set(cet_unique) & set(kaoyan_unique))}")

    if len(core) < TARGETS["core3000"]:
        print("NGSL + COCA could not fill 3000 words", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
