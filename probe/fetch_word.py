#!/usr/bin/env python3
"""最小探针：取一个词条并归一化成自己的 schema。

用途：验证 definition.ajax 的解析契约，也是将来网关（Ktor/Node）的参考实现。
只做单次、按需查询；不要用它批量抓取，也不要内置进随 App 分发的资源里。

用法：
  python3 fetch_word.py ubiquitous            # 输出归一化 JSON
  python3 fetch_word.py ubiquitous --raw      # 输出原始 HTML 片段，校对选择器用
  python3 fetch_word.py ubiquitous --tree     # 输出 DOM 骨架（只有标签和 class）
"""
from __future__ import annotations

import argparse
import html
import json
import re
import sys
import urllib.parse
import urllib.request
from html.parser import HTMLParser

UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/131 Safari/537.36"
AJAX = "https://www.vocabulary.com/dictionary/definition.ajax?search={q}&lang=en"
AUDIO = "https://audio.vocabulary.com/1.0/{region}/{path}.mp3"
VOID = {"br", "img", "input", "meta", "link", "hr", "source", "wbr", "area", "base", "col", "embed"}


class Node:
    __slots__ = ("tag", "attrs", "children", "parent")

    def __init__(self, tag: str, attrs: dict[str, str], parent: "Node | None" = None):
        self.tag, self.attrs, self.parent = tag, attrs, parent
        self.children: list["Node | str"] = []

    @property
    def classes(self) -> set[str]:
        return set((self.attrs.get("class") or "").split())

    def has(self, cls: str) -> bool:
        return cls in self.classes

    def text(self) -> str:
        out = []
        for c in self.children:
            out.append(c if isinstance(c, str) else c.text())
        return re.sub(r"\s+", " ", html.unescape("".join(out))).strip()

    def find_all(self, tag: str | None = None, cls: str | None = None) -> list["Node"]:
        hits = []
        for c in self.children:
            if isinstance(c, Node):
                if (tag is None or c.tag == tag) and (cls is None or c.has(cls)):
                    hits.append(c)
                hits.extend(c.find_all(tag, cls))
        return hits

    def find(self, tag: str | None = None, cls: str | None = None) -> "Node | None":
        for c in self.children:
            if isinstance(c, Node):
                if (tag is None or c.tag == tag) and (cls is None or c.has(cls)):
                    return c
                if (found := c.find(tag, cls)) is not None:
                    return found
        return None


class TreeBuilder(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.root = Node("#document", {})
        self.stack = [self.root]

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        node = Node(tag, {k: (v or "") for k, v in attrs}, self.stack[-1])
        self.stack[-1].children.append(node)
        if tag not in VOID:
            self.stack.append(node)

    def handle_startendtag(self, tag: str, attrs) -> None:
        self.stack[-1].children.append(Node(tag, {k: (v or "") for k, v in attrs}, self.stack[-1]))

    def handle_endtag(self, tag: str) -> None:
        for i in range(len(self.stack) - 1, 0, -1):
            if self.stack[i].tag == tag:
                del self.stack[i:]
                return

    def handle_data(self, data: str) -> None:
        if data.strip():
            self.stack[-1].children.append(data)


def fetch(word: str) -> str:
    url = AJAX.format(q=urllib.parse.quote(word))
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=20) as r:
        return r.read().decode("utf-8", "replace")


def ipa_from(block: Node) -> str | None:
    for span in block.find_all("span", "span-replace-h3"):
        t = span.text()
        if "/" in t:
            return t
    m = re.search(r"/[^/]{1,40}/", block.text())
    return m.group(0) if m else None


def region_of(block: Node) -> str:
    return "uk" if block.find(cls="uk-flag-icon") else "us"


def parse(page: str, word: str) -> dict:
    tb = TreeBuilder()
    tb.feed(page)
    root = tb.root.find(cls="wordPage")
    if root is None:  # 查不到词时站点返回的是搜索建议页
        return {"word": word, "found": False, "suggestions": [a.text() for a in tb.root.find_all("a")][:10]}

    ipa: dict[str, str] = {}
    audio: dict[str, str] = {}
    for block in root.find_all("div", "ipa-with-audio"):
        region = region_of(block)
        if (v := ipa_from(block)) and region not in ipa:
            ipa[region] = v
        if (a := block.find("a", "audio")) and (path := a.attrs.get("data-audio")):
            audio.setdefault(region, AUDIO.format(region=region, path=path))

    senses = []
    for li in root.find_all("li", "sense"):
        defn = li.find(cls="defContent")
        if defn is None:
            continue
        detail = defn.find("span", "detail")
        pos = li.find("div", "pos-icon")
        examples = [d.text() for d in defn.find_all(cls="div-replace-dd") if d.text()]
        senses.append({
            "pos": pos.text() if pos else None,
            "definition": detail.text() if detail else None,
            "examples": examples[:4],
        })

    related = []
    for box in root.find_all("div", "box-vocab-item"):
        title = box.find("a", "vlist-title")
        blurb = box.find("p", "vlist-txt")
        if title and title.text():
            related.append({"title": title.text(), "blurb": blurb.text() if blurb else None})

    videos = [v.attrs.get("src") or v.attrs.get("data-src") or "" for v in root.find_all("video", "pron-video")]

    def one(cls: str) -> str | None:
        n = root.find(cls=cls)
        return n.text() if n and n.text() else None

    return {
        "word": root.attrs.get("data-word") or word,
        "found": True,
        "ipa": ipa,
        "audio": audio,
        "forms": one("word-forms"),
        "short": one("short"),
        "long": one("long"),
        "senses": senses,
        "videos": [v for v in videos if v],
        "relatedLists": related[:8],
        "adjacent": {"prev": root.attrs.get("data-prev"), "next": root.attrs.get("data-next")},
        "source": {"url": f"https://www.vocabulary.com/dictionary/{urllib.parse.quote(word)}"},
    }


def tree(node: Node, depth: int = 0, limit: list[int] | None = None) -> None:
    limit = limit or [160]
    for c in node.children:
        if not isinstance(c, Node) or limit[0] <= 0:
            continue
        limit[0] -= 1
        cls = " ".join(sorted(c.classes)[:3])
        print("  " * min(depth, 14) + f"<{c.tag}" + (f' .{cls}' if cls else "") + ">")
        tree(c, depth + 1, limit)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("word")
    ap.add_argument("--raw", action="store_true", help="输出原始 HTML 片段")
    ap.add_argument("--tree", action="store_true", help="输出 DOM 骨架")
    args = ap.parse_args()

    page = fetch(args.word)
    if args.raw:
        sys.stdout.write(page)
    elif args.tree:
        tb = TreeBuilder()
        tb.feed(page)
        tree(tb.root.find(cls="wordPage") or tb.root)
    else:
        json.dump(parse(page, args.word), sys.stdout, ensure_ascii=False, indent=2)
        sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
